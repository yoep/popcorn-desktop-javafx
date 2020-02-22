package com.github.yoep.video.vlc.conditions;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * {@link Conditional} that only matches when the ARM video player has not been disabled by option {@link OnArmVideoEnabled#DISABLE_OPTION}.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnArmVideoEnabled.class)
public @interface ConditionalOnArmVideoEnabled {
}
