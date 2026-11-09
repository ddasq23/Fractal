#include "Cafe/HW/Latte/Core/LattePerfLog.h"
#include "Cafe/HW/Latte/Renderer/Vulkan/VulkanRenderer.h"

// Occlusion queries are split into fragments that live inside the game's render passes: a fragment begins when a render
// pass begins (or when the query begins inside one) and ends right before that pass ends (or when the query ends).
// Query indices are reset in batches at the start of a command buffer and the results are read on the host once the
// command buffer has finished. So beginning or ending a query never ends the render pass, which on tiled mobile GPUs
// costs a store and a reload of all attachments. Draws only happen inside render passes, so no samples are lost.
class LatteQueryObjectVk : public LatteQueryObject
{
	friend class VulkanRenderer;

	LatteQueryObjectVk(VulkanRenderer* rendererVk) : m_rendererVk(rendererVk)
	{

	};

	bool getResult(uint64& numSamplesPassed) override;
	void begin() override;
	void end() override;
	void beginFragment();
	void endFragment();
	void handleFinishedFragments();

	static constexpr uint32 INVALID_QUERY_INDEX = 0xFFFFFFFF;
	uint32 acquireQueryIndex();
	void releaseQueryIndex(uint32 queryIndex);

private:
	struct queryFragment
	{
		uint32 queryIndex;
		uint64 m_finishCommandBuffer;
		bool isFinished;
	};

	VulkanRenderer* m_rendererVk;
	std::vector<queryFragment> list_queryFragments;
	bool m_vkQueryEnded{};
	bool m_hasActiveQuery{};
	bool m_hasActiveFragment{};
	uint64 m_finishCommandBuffer;
	uint64 m_acccumulatedSum;
};

bool LatteQueryObjectVk::getResult(uint64& numSamplesPassed)
{
	if (!m_vkQueryEnded)
		return false;
	if (!m_rendererVk->HasCommandBufferFinished(m_finishCommandBuffer))
		return false;
	handleFinishedFragments();
	cemu_assert_debug(list_queryFragments.empty());
	numSamplesPassed = m_acccumulatedSum;
	return true;
}

void LatteQueryObjectVk::beginFragment()
{
	cemu_assert_debug(m_rendererVk->m_state.activeRenderpassFBO);
	cemu_assert_debug(!m_hasActiveFragment);
	handleFinishedFragments();
	uint32 newQueryIndex = acquireQueryIndex();
	if (newQueryIndex == INVALID_QUERY_INDEX)
		return; // pool exhausted: the samples of this render pass are lost

	queryFragment qf{};
	qf.queryIndex = newQueryIndex;
	qf.isFinished = false;
	qf.m_finishCommandBuffer = 0;
	list_queryFragments.emplace_back(qf);

	vkCmdBeginQuery(m_rendererVk->m_state.currentCommandBuffer, m_rendererVk->m_occlusionQueries.queryPool, newQueryIndex, VK_QUERY_CONTROL_PRECISE_BIT);
	m_hasActiveFragment = true;
}

void LatteQueryObjectVk::begin()
{
	m_vkQueryEnded = false;
	m_hasActiveQuery = true;
	if (m_rendererVk->m_state.activeRenderpassFBO)
		beginFragment();
	// otherwise the first fragment begins with the next render pass
}

void LatteQueryObjectVk::endFragment()
{
	cemu_assert_debug(m_hasActiveFragment);
	uint32 queryIndex = list_queryFragments.back().queryIndex;
	vkCmdEndQuery(m_rendererVk->m_state.currentCommandBuffer, m_rendererVk->m_occlusionQueries.queryPool, queryIndex);
	list_queryFragments.back().m_finishCommandBuffer = m_rendererVk->GetCurrentCommandBufferId();
	list_queryFragments.back().isFinished = true;
	m_hasActiveFragment = false;
}

void LatteQueryObjectVk::handleFinishedFragments()
{
	// remove finished fragments and add to m_acccumulatedSum
	while (!list_queryFragments.empty())
	{
		auto& it = list_queryFragments.front();
		if (!it.isFinished)
			break;
		if (!m_rendererVk->HasCommandBufferFinished(it.m_finishCommandBuffer))
			break;
		uint64 samples = 0;
		// the command buffer has finished, so the result is available
		vkGetQueryPoolResults(m_rendererVk->GetLogicalDevice(), m_rendererVk->m_occlusionQueries.queryPool, it.queryIndex, 1, sizeof(uint64), &samples, sizeof(uint64), VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT);
		m_acccumulatedSum += samples;
		releaseQueryIndex(it.queryIndex);
		list_queryFragments.erase(list_queryFragments.begin());
	}
}

uint32 LatteQueryObjectVk::acquireQueryIndex()
{
	auto& available = m_rendererVk->m_occlusionQueries.list_availableQueryIndices;
	if (available.empty())
	{
		static bool s_logged = false;
		if (!s_logged)
			cemuLog_log(LogType::Force, "Vulkan-Error: Exhausted query pool");
		s_logged = true;
		return INVALID_QUERY_INDEX;
	}
	uint32 queryIndex = available.back();
	available.pop_back();
	return queryIndex;
}

void LatteQueryObjectVk::releaseQueryIndex(uint32 queryIndex)
{
	// reset at the start of the next command buffer, then available again
	m_rendererVk->m_occlusionQueries.list_pendingResetQueryIndices.emplace_back(queryIndex);
}

void LatteQueryObjectVk::end()
{
	if(m_hasActiveFragment)
		endFragment();
	m_vkQueryEnded = true;
	m_hasActiveQuery = false;
	m_finishCommandBuffer = m_rendererVk->GetCurrentCommandBufferId();
	m_rendererVk->m_occlusionQueries.m_lastCommandBuffer = m_finishCommandBuffer;
	m_rendererVk->RequestSubmitSoon(); // make sure the current command buffer gets submitted soon
	m_rendererVk->RequestSubmitOnIdle();
}

LatteQueryObject* VulkanRenderer::occlusionQuery_create()
{
	// create query pool if it doesn't already exist
	if(m_occlusionQueries.queryPool == VK_NULL_HANDLE)
	{
		VkQueryPoolCreateInfo queryPoolCreateInfo{};
		queryPoolCreateInfo.sType = VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO;
		queryPoolCreateInfo.flags = 0;
		queryPoolCreateInfo.queryType = VK_QUERY_TYPE_OCCLUSION;
		queryPoolCreateInfo.queryCount = OCCLUSION_QUERY_POOL_SIZE;
		queryPoolCreateInfo.pipelineStatistics = 0;
		auto r = vkCreateQueryPool(m_logicalDevice, &queryPoolCreateInfo, nullptr, &m_occlusionQueries.queryPool);
		if (r != VK_SUCCESS)
		{
			cemuLog_log(LogType::Force, "Vulkan-Error: Failed to create query pool with error {}", (sint32)r);
			return nullptr;
		}
		// every query must be reset before its first use. This is the only time a query ends the render pass
		draw_endRenderPass(PerfLog::RenderPassEnd::Query);
		vkCmdResetQueryPool(m_state.currentCommandBuffer, m_occlusionQueries.queryPool, 0, OCCLUSION_QUERY_POOL_SIZE);
		m_occlusionQueries.list_availableQueryIndices.clear();
		m_occlusionQueries.list_pendingResetQueryIndices.clear();
		for (sint32 i = OCCLUSION_QUERY_POOL_SIZE - 1; i >= 0; i--)
			m_occlusionQueries.list_availableQueryIndices.emplace_back(i);
	}
	LatteQueryObjectVk* queryObjVk = nullptr;
	if (m_occlusionQueries.list_cachedQueries.empty())
	{
		queryObjVk = new LatteQueryObjectVk(this);
	}
	else
	{
		queryObjVk = m_occlusionQueries.list_cachedQueries.front();
		m_occlusionQueries.list_cachedQueries.erase(m_occlusionQueries.list_cachedQueries.begin()+0);
	}
	queryObjVk->queryEnded = false;
	queryObjVk->queryEventStart = 0;
	queryObjVk->queryEventEnd = 0;
	queryObjVk->m_vkQueryEnded = false;
	queryObjVk->m_hasActiveQuery = false;
	queryObjVk->m_hasActiveFragment = false;
	queryObjVk->m_acccumulatedSum = 0;
	cemu_assert_debug(queryObjVk->list_queryFragments.empty()); // query fragment list should always be cleared in _destroy()
	m_occlusionQueries.list_currentlyActiveQueries.emplace_back(queryObjVk);
	return queryObjVk;
}

void VulkanRenderer::occlusionQuery_destroy(LatteQueryObject* queryObj)
{
	LatteQueryObjectVk* queryObjVk = static_cast<LatteQueryObjectVk*>(queryObj);
	if (queryObjVk->m_hasActiveFragment) // destroyed while still running: close it so the render pass stays valid
		queryObjVk->endFragment();
	m_occlusionQueries.list_currentlyActiveQueries.erase(std::remove(m_occlusionQueries.list_currentlyActiveQueries.begin(), m_occlusionQueries.list_currentlyActiveQueries.end(), queryObj), m_occlusionQueries.list_currentlyActiveQueries.end());
	m_occlusionQueries.list_cachedQueries.emplace_back(queryObjVk);
	// a fragment's index may still be in use by a command buffer in flight. That is fine: it is only reset by a later
	// command buffer, which the GPU runs after this one
	for (auto& it : queryObjVk->list_queryFragments)
		queryObjVk->releaseQueryIndex(it.queryIndex);
	queryObjVk->list_queryFragments.clear();
}

void VulkanRenderer::occlusionQuery_flush()
{
	PerfLog::GpuWaitScope waitScope(PerfLog::GpuWaitReason::Query);
	WaitCommandBufferFinished(m_occlusionQueries.m_lastCommandBuffer);
}

void VulkanRenderer::occlusionQuery_updateState()
{
	// check for finished command buffers here since query states are tied to buffers
	ProcessFinishedCommandBuffers();
}

void VulkanRenderer::occlusionQuery_notifyEndCommandBuffer()
{
	// fragments end with their render pass, which ends before the command buffer does
	for (auto& it : m_occlusionQueries.list_currentlyActiveQueries)
		cemu_assert_debug(!it->m_hasActiveFragment);
}

void VulkanRenderer::occlusionQuery_notifyBeginCommandBuffer()
{
	// reset the indices released since the last command buffer, outside of any render pass
	auto& pending = m_occlusionQueries.list_pendingResetQueryIndices;
	if (pending.empty() || m_occlusionQueries.queryPool == VK_NULL_HANDLE)
		return;
	std::sort(pending.begin(), pending.end());
	size_t rangeStart = 0;
	for (size_t i = 1; i <= pending.size(); i++)
	{
		if (i == pending.size() || pending[i] != pending[i - 1] + 1)
		{
			vkCmdResetQueryPool(m_state.currentCommandBuffer, m_occlusionQueries.queryPool, pending[rangeStart], (uint32)(i - rangeStart));
			rangeStart = i;
		}
	}
	auto& available = m_occlusionQueries.list_availableQueryIndices;
	available.insert(available.end(), pending.begin(), pending.end());
	pending.clear();
}

void VulkanRenderer::occlusionQuery_notifyRenderPassBegin()
{
	for (auto& it : m_occlusionQueries.list_currentlyActiveQueries)
		if (it->m_hasActiveQuery)
			it->beginFragment();
}

void VulkanRenderer::occlusionQuery_notifyRenderPassEnd()
{
	for (auto& it : m_occlusionQueries.list_currentlyActiveQueries)
		if (it->m_hasActiveFragment)
			it->endFragment();
}
