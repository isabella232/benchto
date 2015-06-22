/*
 * Copyright 2013-2015, Teradata, Inc. All rights reserved.
 */
package com.teradata.benchmark.driver.macro;

import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

/**
 * Executes macros defined in application yaml file.
 */
public interface MacroService
{
    default void runMacro(String macroName)
    {
        runMacro(macroName, ImmutableMap.of());
    }

    default void runMacros(List<String> macros)
    {
        macros.forEach(this::runMacro);
    }

    void runMacro(String macroName, Map<String, String> environment);
}

