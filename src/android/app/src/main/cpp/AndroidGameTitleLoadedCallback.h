#pragma once

#include "GameTitleLoader.h"
#include "JNIUtils.h"
#include <android/bitmap.h>

/** Makes NativeGameTitles.Game objects (with their icon bitmap) from the loader's Game. */
class JavaGameBuilder
{
	jmethodID m_gameConstructorMID;
	JNIUtils::Scopedjclass m_gamejclass{"dev/dfd/fractal/nativeinterface/NativeGameTitles$Game"};
	jmethodID m_createBitmapMID;
	JNIUtils::Scopedjclass m_bitmapClass{"android/graphics/Bitmap"};
	JNIUtils::Scopedjobject m_bitmapFormat;

  public:
	JavaGameBuilder()
	{
		JNIEnv* env = JNIUtils::GetEnv();
		m_bitmapFormat = JNIUtils::GetEnumValue(env, "android/graphics/Bitmap$Config", "ARGB_8888");
		m_gameConstructorMID = env->GetMethodID(*m_gamejclass, "<init>", "(JLjava/lang/String;Ljava/lang/String;SSISSSIZLandroid/graphics/Bitmap;)V");
		m_createBitmapMID = env->GetStaticMethodID(*m_bitmapClass, "createBitmap", "([IIILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
	}

	/** A local reference the caller deletes (or returns to Java). */
	jobject Build(JNIEnv* env, const Game& game, const std::shared_ptr<Image>& icon)
	{
		jstring name = JNIUtils::ToJString(env, game.name);
		jstring path = game.path.has_value() ? JNIUtils::ToJString(env, game.path.value()) : nullptr;
		jobject bitmap = nullptr;
		sint32 lastPlayedYear = 0, lastPlayedMonth = 0, lastPlayedDay = 0;
		if (game.lastPlayed.has_value())
		{
			lastPlayedYear = static_cast<sint32>(game.lastPlayed->year());
			lastPlayedMonth = static_cast<uint32>(game.lastPlayed->month());
			lastPlayedDay = static_cast<uint32>(game.lastPlayed->day());
		}

		if (icon)
		{
			jintArray jIconData = env->NewIntArray(icon->width * icon->height);
			env->SetIntArrayRegion(jIconData, 0, icon->width * icon->height, icon->colors);
			bitmap = env->CallStaticObjectMethod(*m_bitmapClass, m_createBitmapMID, jIconData, icon->width, icon->height, *m_bitmapFormat);
			env->DeleteLocalRef(jIconData);
		}

		jobject gamejobject = env->NewObject(
			*m_gamejclass,
			m_gameConstructorMID,
			game.titleId,
			path,
			name,
			game.version,
			game.dlc,
			static_cast<sint32>(game.region),
			lastPlayedYear,
			lastPlayedMonth,
			lastPlayedDay,
			game.minutesPlayed,
			game.isFavorite,
			bitmap);
		if (bitmap != nullptr)
			env->DeleteLocalRef(bitmap);
		if (path != nullptr)
			env->DeleteLocalRef(path);
		env->DeleteLocalRef(name);
		return gamejobject;
	}
};

class AndroidGameTitleLoadedCallback : public GameTitleLoadedCallback
{
	jmethodID m_onGameTitleLoadedMID;
	JNIUtils::Scopedjobject m_gameTitleLoadedCallbackObj;
	JavaGameBuilder m_builder;

  public:
	AndroidGameTitleLoadedCallback(jmethodID onGameTitleLoadedMID, jobject gameTitleLoadedCallbackObj)
		: m_onGameTitleLoadedMID(onGameTitleLoadedMID),
		  m_gameTitleLoadedCallbackObj(gameTitleLoadedCallbackObj)
	{
	}

	void OnTitleLoaded(const Game& game, const std::shared_ptr<Image>& icon) override
	{
		JNIEnv* env = JNIUtils::GetEnv();
		jobject gamejobject = m_builder.Build(env, game, icon);
		env->CallVoidMethod(*m_gameTitleLoadedCallbackObj, m_onGameTitleLoadedMID, gamejobject);
		env->DeleteLocalRef(gamejobject);
	}
};
