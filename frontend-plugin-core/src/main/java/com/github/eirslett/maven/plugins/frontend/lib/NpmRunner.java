package com.github.eirslett.maven.plugins.frontend.lib;

import java.util.ArrayList;
import java.util.List;

public interface NpmRunner {
    void execute(String args) throws TaskRunnerException;
}

final class DefaultNpmRunner extends NodeTaskExecutor implements NpmRunner {
    static final String TASK_NAME = "npm";

    public DefaultNpmRunner(NodeExecutorConfig config, ProxyConfig proxy) {
        super(config, TASK_NAME, config.getNpmPath().getAbsolutePath(), buildArguments(proxy));
    }

    private static List<String> buildArguments(ProxyConfig proxy) {
        List<String> arguments = new ArrayList<String>();
        arguments.add("--color=false");

        String npmRegistryURL = System.getProperty("npmRegistryURL");
        if (npmRegistryURL != null)
        {
            arguments.add ("--registry=" + npmRegistryURL);
        }

        if (proxy != null) {
            if(proxy.isSecure()){
                arguments.add("--https-proxy=" + proxy.getUri().toString());
            } else {
                arguments.add("--proxy=" + proxy.getUri().toString());
            }
        }
        return arguments;
    }
}
