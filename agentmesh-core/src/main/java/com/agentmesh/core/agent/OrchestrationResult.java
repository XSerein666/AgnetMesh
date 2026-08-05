package com.agentmesh.core.agent;

import java.util.Map;

/**
 * 编排结果
 */
public class OrchestrationResult {
    private String finalOutput;
    private Map<String, Object> intermediateResults;
    private boolean success;
    private String errorMessage;

    public String getFinalOutput() { return finalOutput; }
    public void setFinalOutput(String finalOutput) { this.finalOutput = finalOutput; }
    public Map<String, Object> getIntermediateResults() { return intermediateResults; }
    public void setIntermediateResults(Map<String, Object> intermediateResults) { this.intermediateResults = intermediateResults; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}