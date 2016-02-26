/*
 * (C) Copyright ParaSoft Corporation 2013.  All rights reserved.
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE OF ParaSoft
 * The copyright notice above does not evidence any
 * actual or intended publication of such source code.
 */

package com.parasoft.environmentmanager.jenkins;

import java.io.IOException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.parasoft.em.client.api.EnvironmentCopy;
import com.parasoft.em.client.api.Environments;
import com.parasoft.em.client.api.EventMonitor;
import com.parasoft.em.client.api.Provisions;
import com.parasoft.em.client.api.Servers;
import com.parasoft.em.client.api.Systems;
import com.parasoft.em.client.impl.EnvironmentCopyImpl;
import com.parasoft.em.client.impl.EnvironmentsImpl;
import com.parasoft.em.client.impl.ProvisionsImpl;
import com.parasoft.em.client.impl.ServersImpl;
import com.parasoft.em.client.impl.SystemsImpl;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import hudson.util.ListBoxModel;

public class EnvironmentManagerBuilder extends Builder {
    private int systemId;
    private int environmentId;
    private int instanceId;
    private boolean copyToServer;
    private String newEnvironmentName;
    private String serverType;
    private int serverId;
    private String serverHost;
    private String serverName;
    private boolean abortOnFailure;
    
    @DataBoundConstructor
    public EnvironmentManagerBuilder(
        int systemId,
        int environmentId,
        int instanceId,
        boolean copyToServer,
        String newEnvironmentName,
        String serverType,
        int serverId,
        String serverHost,
        String serverName,
        boolean abortOnFailure)
    {
        super();
        this.systemId = systemId;
        this.environmentId = environmentId;
        this.instanceId = instanceId;
        this.copyToServer = copyToServer;
        this.newEnvironmentName = newEnvironmentName;
        this.serverType = serverType;
        this.serverId = serverId;
        this.serverHost = serverHost;
        this.serverName = serverName;
        this.abortOnFailure = abortOnFailure;
    }
    
    public int getSystemId() {
        return systemId;
    }
    
    public int getEnvironmentId() {
        return environmentId;
    }
    
    public int getInstanceId() {
        return instanceId;
    }
    
    public boolean isCopyToServer() {
        return copyToServer;
    }
    
    public String getNewEnvironmentName() {
        return newEnvironmentName;
    }
    
    public boolean isServerType(String type) {
        if ((serverType == null) || serverType.isEmpty()) {
            return "registered".equals(type);
        }
        return serverType.equals(type);
    }
    
    public int getServerId() {
        return serverId;
    }
    
    public String getServerHost() {
        return serverHost;
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public boolean isAbortOnFailure() {
        return abortOnFailure;
    }
    
    @Override
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher,
            final BuildListener listener) throws InterruptedException, IOException
    {
        EnvVars envVars = build.getEnvironment(listener);
        int targetEnvironmentId = environmentId;
        int targetInstanceId = instanceId;
        Environments environments = new EnvironmentsImpl(getDescriptor().getEmUrl(), getDescriptor().getUsername(), getDescriptor().getPassword().getPlainText());
        JSONObject instance = environments.getEnvironmentInstance(environmentId, instanceId);
        if (copyToServer) {
            int targetServerId = 0;
            String targetServerName = envVars.expand(serverName);
            String targetServerHost = envVars.expand(serverHost);
            if (isServerType("registered")) {
                targetServerId = serverId;
            } else {
                boolean waitingNotFoundMessageShown = false;
                boolean waitingOfflineMessageShown = false;
                String status = null;
                while (targetServerId == 0) {
                    Servers servers = new ServersImpl(getDescriptor().getEmUrl(), getDescriptor().getUsername(), getDescriptor().getPassword().getPlainText());
                    JSONObject response = servers.getServers();
                    if (response.has("servers")) {
                        JSONArray envArray = response.getJSONArray("servers");
                        for (Object o : envArray) {
                            JSONObject server = (JSONObject) o;
                            if (isServerType("name")) {
                                String name = server.getString("name");
                                if (name.indexOf(targetServerName) >= 0) {
                                    targetServerId = server.getInt("id");
                                    status = server.optString("status");
                                }
                                if (name.equalsIgnoreCase(targetServerName)) {
                                    break;
                                }
                            } else if (isServerType("host")) {
                                String host = server.getString("host");
                                if (host.indexOf(targetServerHost) >= 0) {
                                    targetServerId = server.getInt("id");
                                    status = server.optString("status");
                                }
                                if (host.equalsIgnoreCase(targetServerHost)) {
                                    break;
                                }
                            }
                        }
                    }
                    if ((targetServerId == 0) && !waitingNotFoundMessageShown) {
                        String errorMessage = "WARNING:  Could not find any Virtualize servers matching ";
                        if (isServerType("name")) {
                            errorMessage += "name:  " + targetServerName;
                        } else if (isServerType("host")) {
                            errorMessage += "host:  " + targetServerHost;
                        }
                        listener.getLogger().println(errorMessage);
                        listener.getLogger().println("Waiting for a machting Virtualize server to register with EM...");
                        waitingNotFoundMessageShown = true;
                    }
                    if ("OFFLINE".equals(status) || "REFRESHING".equals(status)) {
                        targetServerId = 0;
                        if (!waitingOfflineMessageShown) {
                            listener.getLogger().println("Waiting for Virtualize server to come online...");
                            waitingOfflineMessageShown = true;
                        }
                    }
                    Thread.sleep(10000); // try again in 10 seconds
                }
            }
            EnvironmentCopy environmentCopy = new EnvironmentCopyImpl(getDescriptor().getEmUrl(), getDescriptor().getUsername(), getDescriptor().getPassword().getPlainText());
            JSONObject copyEvent = environmentCopy.createEnvironmentCopy(environmentId, targetServerId, envVars.expand(newEnvironmentName));
            boolean copyResult = environmentCopy.monitorEvent(copyEvent, new EventMonitor() {
                public void logMessage(String message) {
                    listener.getLogger().println(message);
                }
            });
            JSONObject copyStatus = environmentCopy.removeCopyStatus(copyEvent.getInt("id"));
            if (!copyResult) {
                return false;
            }
            targetEnvironmentId = copyStatus.getInt("environmentId");
            String instanceName = instance.getString("name");
            JSONObject copiedInstances = environments.getEnvironmentInstances(targetEnvironmentId);
            if (copiedInstances.has("instances")) {
                JSONArray instArray = copiedInstances.getJSONArray("instances");
                for (int i = 0; i < instArray.size(); i++) {
                    JSONObject inst = instArray.getJSONObject(i);
                    if (instanceName.equals(inst.getString("name"))) {
                        targetInstanceId = inst.getInt("id");
                    }
                }
            }
        }
        listener.getLogger().println("Executing provisioning action on " + getDescriptor().getEmUrl());
        Provisions provisions = new ProvisionsImpl(getDescriptor().getEmUrl(), getDescriptor().getUsername(), getDescriptor().getPassword().getPlainText());
        JSONObject event = provisions.createProvisionEvent(targetEnvironmentId, targetInstanceId, abortOnFailure);
        boolean result = provisions.monitorEvent(event, new EventMonitor() {
            public void logMessage(String message) {
                listener.getLogger().println(message);
            }
        });
        String baseUrl = getDescriptor().getEmUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        
        JSONObject eventResult = provisions.getProvisions(event.getInt("eventId"));
        JSONArray steps = eventResult.getJSONArray("steps");
        int failed = 0;
        for (int i = 0; i < steps.size(); i++) {
            JSONObject step = steps.getJSONObject(i);
            if ("error".equals(step.getString("result"))) {
                failed++;
            }
        }
        
        String environmentUrl = baseUrl + "environments/" + targetEnvironmentId;
        build.addAction(new ProvisioningEventAction(build, instance.getString("name"), environmentUrl, steps.size(), failed));
        return result;
    }
    
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String emUrl;
        private String username;
        private Secret password;
        
        public DescriptorImpl() {
            load();
        }
        
        public String getEmUrl() {
            return emUrl;
        }
        
        public String getUsername() {
            return username;
        }
        
        public Secret getPassword() {
            return password;
        }
        
        public FormValidation doTestConnection(@QueryParameter String emUrl, @QueryParameter String username, @QueryParameter String password) {
            Secret secret = Secret.fromString(password);
            try {
                Environments environments = new EnvironmentsImpl(emUrl, username, secret.getPlainText());
                environments.getEnvironments();
            } catch (IOException e) {
                // First try to re-run while appending /em
                if (emUrl.endsWith("/")) {
                    emUrl += "em";
                } else {
                    emUrl += "/em";
                }
                try {
                    Environments environments = new EnvironmentsImpl(emUrl, username, secret.getPlainText());
                    environments.getEnvironments();
                    return FormValidation.ok("Successfully connected to Environment Manager");
                } catch (IOException e2) {
                    // return the original exception
                }
                return FormValidation.error(e, "Unable to connect to Environment Manager Server");
            }
            return FormValidation.ok("Successfully connected to Environment Manager");
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Parasoft Environment Manager";
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            emUrl = json.getString("emUrl");
            username = json.getString("username");
            password = Secret.fromString(json.getString("password"));
            
            // Test the emUrl, appending "/em" if necessary
            try {
                Environments environments = new EnvironmentsImpl(emUrl, username, password.getPlainText());
                environments.getEnvironments();
            } catch (IOException e) {
                // First try to re-run while appending the default context path /em
                String testUrl = emUrl;
                if (testUrl.endsWith("/")) {
                    testUrl += "em";
                } else {
                    testUrl += "/em";
                }
                try {
                    Environments environments = new EnvironmentsImpl(testUrl, username, password.getPlainText());
                    environments.getEnvironments();
                    emUrl = testUrl;
                } catch (IOException e2) {
                    throw new FormException("Unable to connect to Environment Manager at " + emUrl, "emUrl");
                }
            }
            
            save();
            return super.configure(req, json);
        }
        
        public ListBoxModel doFillSystemIdItems() {
            ListBoxModel m = new ListBoxModel();
            try {
                if (emUrl != null) {
                    Systems systems = new SystemsImpl(emUrl, username, password.getPlainText());
                    JSONObject envs = systems.getSystems();
                    if (envs.has("systems")) {
                        JSONArray envArray = envs.getJSONArray("systems");
                        for (Object o : envArray) {
                            JSONObject system = (JSONObject) o;
                            String name = system.getString("name");
                            if (system.has("version")) {
                                name += " (" + system.getString("version") + ")";
                            }
                            m.add(name, system.getString("id"));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return m;
        }
        
        public ListBoxModel doFillEnvironmentIdItems(@QueryParameter int systemId) {
            ListBoxModel m = new ListBoxModel();
            try {
                if (emUrl != null) {
                    Environments environments = new EnvironmentsImpl(emUrl, username, password.getPlainText());
                    JSONObject envs = environments.getEnvironments();
                    if (envs.has("environments")) {
                        JSONArray envArray = envs.getJSONArray("environments");
                        for (Object o : envArray) {
                            JSONObject env = (JSONObject) o;
                            if (env.getInt("systemId") == systemId) {
                                String name = env.getString("name");
                                if (env.has("version")) {
                                    name += " (" + env.getString("version") + ")";
                                }
                                m.add(name, env.getString("id"));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return m;
        }
        
        public ListBoxModel doFillInstanceIdItems(@QueryParameter int environmentId) {
            ListBoxModel m = new ListBoxModel();
            try {
                Environments environments = new EnvironmentsImpl(emUrl, username, password.getPlainText());
                JSONObject instances = environments.getEnvironmentInstances(environmentId);
                if (instances.has("instances")) {
                    JSONArray instArray = instances.getJSONArray("instances");
                    for (Object o : instArray) {
                        JSONObject inst = (JSONObject) o;
                        m.add(inst.getString("name"), inst.getString("id"));
                    }
                }
            } catch (IOException e) {
            }
            return m;
        }

        public ListBoxModel doFillServerIdItems() {
            ListBoxModel m = new ListBoxModel();
            try {
                if (emUrl != null) {
                    Servers servers = new ServersImpl(emUrl, username, password.getPlainText());
                    JSONObject response = servers.getServers();
                    if (response.has("servers")) {
                        JSONArray envArray = response.getJSONArray("servers");
                        for (Object o : envArray) {
                            JSONObject server = (JSONObject) o;
                            String name = server.getString("name");
                            String host = server.getString("host");
                            if (!name.equals(host)) {
                                name += " (" + host + ':' + server.getInt("port") + ')';
                            }
                            m.add(name, server.getString("id"));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return m;
        }
    }
}
