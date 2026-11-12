package me.khajiitos.servercountryflags.common.util;

import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public record FlagRenderInfo(String countryCode, double flagAspectRatio, List<FormattedCharSequence> tooltip) {}
