package org.jvnet.hudson.plugins.m2release.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.util.StaplerReferer;
import org.jvnet.hudson.plugins.m2release.M2ReleaseArgumentsAction;
import org.jvnet.hudson.plugins.m2release.M2ReleaseBadgeAction;
import org.jvnet.hudson.plugins.m2release.M2ReleaseBuildWrapper;
import org.jvnet.hudson.plugins.m2release.ReleaseCause;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.google.inject.Inject;

import hudson.AbortException;
import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildableItemWithBuildWrappers;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * Created by e3cmea on 1/3/17.
 *
 * @author Alexey Merezhin
 */
public class M2ReleaseStep extends AbstractStepImpl {
    private static final Logger LOGGER = Logger.getLogger(M2ReleaseStep.class.getName());

    private String job;
    private List<ParameterValue> parameters;

    @DataBoundConstructor
    public M2ReleaseStep(String job) {
        this.job = job;
        parameters = new ArrayList<>();
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public List<ParameterValue> getParameters() {
        return parameters;
    }

    @DataBoundSetter public void setParameters(List<ParameterValue> parameters) {
        this.parameters = parameters;
    }

    public static class Execution extends AbstractStepExecutionImpl {
        @StepContextParameter private transient Run<?,?> invokingRun;
        @StepContextParameter private transient TaskListener listener;

        @Inject(optional=true) transient M2ReleaseStep step;

        private List<ParameterValue> updateParametersWithDefaults(AbstractProject project,
                List<ParameterValue> parameters) throws AbortException {

            if (project instanceof BuildableItemWithBuildWrappers) {
                M2ReleaseBuildWrapper wrapper = ((BuildableItemWithBuildWrappers) project).getBuildWrappersList()
                                                                                   .get(M2ReleaseBuildWrapper.class);
                if (wrapper != null) {
                    /* TODO
                    for (ParameterDefinition pd : wrapper.getParameterDefinitions()) {
                        boolean parameterExists = false;
                        for (ParameterValue pv : parameters) {
                            if (pv.getName()
                                  .equals(pd.getName())) {
                                parameterExists = true;
                                break;
                            }
                        }
                        if (!parameterExists) {
                            parameters.add(pd.getDefaultParameterValue());
                        }
                    }
                    */
                } else {
                    throw new AbortException("Job doesn't have release plugin configuration");
                }
            }
            return parameters;
        }

        @Override
        public boolean start() throws Exception {
            if (step.getJob() == null) {
                throw new AbortException("Job name is not defined.");
            }

            final AbstractProject project = Jenkins.getActiveInstance()
                                                   .getItem(step.getJob(), invokingRun.getParent(), AbstractProject.class);
            if (project == null) {
                throw new AbortException("No parametrized job named " + step.getJob() + " found");
            }
            listener.getLogger().println("Releasing project: " + ModelHyperlinkNote.encodeTo(project));


            boolean isDryRun = false;
            String releaseVersion = "1.0";
            String developmentVersion = "1.1";
            boolean closeNexusStage = false;
            String repoDescription = "repo desc";
            String scmUsername = "username";
            String scmPassword = "password";
            String scmTag = "scm_tag";
            String scmCommentPrefix = "comment prefix";
            boolean appendHusonUserName = false;

            List<ParameterValue> values = new ArrayList<ParameterValue>();

            // schedule release build
            ParametersAction parameters = new ParametersAction(values);

            M2ReleaseArgumentsAction arguments = new M2ReleaseArgumentsAction();
            arguments.setDryRun(isDryRun);

            arguments.setReleaseVersion(releaseVersion);
            arguments.setDevelopmentVersion(developmentVersion);

            arguments.setCloseNexusStage(closeNexusStage);
            arguments.setRepoDescription(repoDescription);
            arguments.setScmUsername(scmUsername);
            arguments.setScmPassword(scmPassword);
            arguments.setScmTagName(scmTag);
            arguments.setScmCommentPrefix(scmCommentPrefix);
            arguments.setAppendHusonUserName(appendHusonUserName);
            arguments.setHudsonUserName(Hudson.getAuthentication().getName());

            List<Action> actions = new ArrayList<>();
            actions.add(parameters);
            actions.add(arguments);
            actions.add(new CauseAction(new ReleaseCause()));

            QueueTaskFuture<?> task = project.scheduleBuild2(0, new Cause.UpstreamCause(invokingRun), actions);
            if (task == null) {
                throw new AbortException("Failed to trigger build of " + project.getFullName());
            }

            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws Exception {
            getContext().onFailure(cause);
        }

        private static final long serialVersionUID = 1L;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "m2release";
        }

        @Override
        public String getDisplayName() {
            return "Trigger M2 release for the job";
        }

        @Override public Step newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            M2ReleaseStep step = (M2ReleaseStep) super.newInstance(req, formData);
            // Cf. ParametersDefinitionProperty._doBuild:
            Object parameter = formData.get("parameter");
            JSONArray params = parameter != null ? JSONArray.fromObject(parameter) : null;
            if (params != null) {
                Jenkins jenkins = Jenkins.getInstance();
                Job<?,?> context = StaplerReferer.findItemFromRequest(Job.class);
                Job<?,?> job = jenkins != null ? jenkins.getItem(step.getJob(), context, Job.class) : null;

                AbstractProject project = Jenkins.getActiveInstance()
                                                       .getItem(step.getJob(), context, AbstractProject.class);

                if (project instanceof BuildableItemWithBuildWrappers) {
                    M2ReleaseBuildWrapper wrapper = ((BuildableItemWithBuildWrappers) project).getBuildWrappersList()
                                                                                                             .get(M2ReleaseBuildWrapper.class);
                    if (wrapper == null) {
                        throw new IllegalArgumentException("Job doesn't have release plugin configuration");
                    }
                    /* TODO
                    List<ParameterDefinition> parameterDefinitions = wrapper.getParameterDefinitions();
                    if (parameterDefinitions != null) {
                        List<ParameterValue> values = new ArrayList<>();
                        for (Object o : params) {
                            JSONObject jo = (JSONObject) o;
                            String name = jo.getString("name");
                            for (ParameterDefinition pd : parameterDefinitions) {
                                if (name.equals(pd.getName())) {
                                    ParameterValue parameterValue = pd.createValue(req, jo);
                                    values.add(parameterValue);
                                }
                            }
                        }
                        step.setParameters(values);
                    }
                    */
                } else {
                    throw new IllegalArgumentException("Wrong job type: " + project.getClass().getName());
                }
            }
            return step;
        }

        public AutoCompletionCandidates doAutoCompleteJob(@AncestorInPath ItemGroup<?> context, @QueryParameter String value) {
            return AutoCompletionCandidates.ofJobNames(ParameterizedJobMixIn.ParameterizedJob.class, value, context);
        }

        @Restricted(DoNotUse.class) // for use from config.jelly
        public String getContext() {
            Job<?,?> job = StaplerReferer.findItemFromRequest(Job.class);
            return job != null ? job.getFullName() : null;
        }
    }
}
