/*
 * Copyright 2013-2015, Teradata, Inc. All rights reserved.
 */
package com.teradata.benchmark.service.rest.requests;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ExecutionStartRequest
{
    private final Map<String, String> attributes;

    @JsonCreator
    public ExecutionStartRequest(@JsonProperty("environmentName") Map<String, String> attributes)
    {
        this.attributes = attributes;
    }

    public Map<String, String> getAttributes()
    {
        return attributes;
    }
}