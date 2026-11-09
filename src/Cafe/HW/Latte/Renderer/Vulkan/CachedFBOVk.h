#pragma once

#include "Cafe/HW/Latte/Core/LatteCachedFBO.h"
#include "Cafe/HW/Latte/Renderer/Vulkan/VKRBase.h"

class CachedFBOVk : public LatteCachedFBO
{
public:
	CachedFBOVk(uint64 key, VkDevice device)
		: LatteCachedFBO(key), m_device(device)
	{
		CreateRenderPass();
		CreateFramebuffer();
		InitDynamicRenderingData();
	}

	~CachedFBOVk();

	static inline FSpinlock s_spinlockDependency;

	VKRObjectFramebuffer* GetFramebufferObj() const
	{
		return m_vkrObjFramebuffer;
	}

	VKRObjectRenderPass* GetRenderPassObj() const
	{
		return m_vkrObjRenderPass;
	}
	VKRObjectRenderPass* GetRenderPassObjNoDepthStore() const
	{
		return m_vkrObjRenderPassNoDepthStore;
	}

	// for KHR_dynamic_rendering
	// dynamic rendering: skip the depth/stencil store of the next render pass (nothing will write them)
	void SetDepthStoreNone(bool none);
	bool m_depthWrittenLastPass{ true }; // the last render pass on this framebuffer wrote depth or stencil

	VkRenderingInfoKHR* GetRenderingInfo()
	{
		return &m_vkRenderingInfo;
	}


	void TrackDependency(class PipelineInfo* pipelineInfo)
	{
		s_spinlockDependency.lock();
		m_usedByPipelines.emplace_back(pipelineInfo);
		s_spinlockDependency.unlock();
	}

	void RemoveDependency(class PipelineInfo* pipelineInfo)
	{
		s_spinlockDependency.lock();
		vectorRemoveByValue(m_usedByPipelines, pipelineInfo);
		s_spinlockDependency.unlock();
	}

	[[nodiscard]] const VkExtent2D& GetExtend() const { return m_extend;}

	struct RendertargetSelfDependencyMask
	{
		VkImageAspectFlags aspectMaskFlags{}; // aspect flags which are simultaneously sampled and written
		bool hasNonPixelSelfDependency{false};
		uint8 colorAttachmentMask{}; // which color attachments (render target indices) are sampled

		VkImageAspectFlags GetAspectMask() const
		{
			return aspectMaskFlags;
		}

		bool HasSelfDependency() const
		{
			return GetAspectMask() != 0;
		}

		bool HasVertexOrGeometrySelfDependency() const
		{
			return hasNonPixelSelfDependency; // vertex or geometry shader samples texture which is written to
		}
	};

	// checks if any of the sampled textures are output by the FBO
	RendertargetSelfDependencyMask CheckForSelfDependency(VkDescriptorSetInfo* vsDS, VkDescriptorSetInfo* gsDS, VkDescriptorSetInfo* psDS) const;

private:

	void CreateRenderPass();
	void CreateFramebuffer();

	void InitDynamicRenderingData();

	VKRObjectTextureView* GetColorBufferImageView(uint32 index);
	VKRObjectTextureView* GetDepthStencilBufferImageView(bool& hasStencil);

	VkDevice m_device;
	VKRObjectRenderPass* m_vkrObjRenderPass{};
	VKRObjectRenderPass* m_vkrObjRenderPassNoDepthStore{}; // same, without the depth/stencil store (null without depth or store op NONE)
	VKRObjectFramebuffer* m_vkrObjFramebuffer{};

	VkExtent2D m_extend;

	// for KHR_dynamic_rendering
	VkRenderingInfoKHR m_vkRenderingInfo;
	VkRenderingAttachmentInfoKHR m_vkColorAttachments[8];
	VkRenderingAttachmentInfoKHR m_vkDepthAttachment;
	VkRenderingAttachmentInfoKHR m_vkStencilAttachment;

	std::vector<class PipelineInfo*> m_usedByPipelines; // PipelineInfo objects which use this renderpass/framebuffer
};
