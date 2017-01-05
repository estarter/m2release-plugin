package org.jvnet.hudson.plugins.m2release.pipeline.M2ReleaseStep.DescriptorImpl

import hudson.model.AbstractProject
import hudson.model.BooleanParameterDefinition
import hudson.model.BuildableItemWithBuildWrappers
import hudson.model.StringParameterDefinition
import org.jvnet.hudson.plugins.m2release.M2ReleaseAction
import org.jvnet.hudson.plugins.m2release.M2ReleaseBuildWrapper

def st = namespace('jelly:stapler')
def l = namespace('/lib/layout')
l.ajax {
    def jobName = request.getParameter('job')
    if (jobName != null) {
        def contextName = request.getParameter('context')
        def context = contextName != null ? app.getItemByFullName(contextName) : null
        def project = app.getItem(jobName, context, AbstractProject)

        if (project != null) {
            if (project instanceof BuildableItemWithBuildWrappers) {
                def wrapper = project.getBuildWrappersList().get(M2ReleaseBuildWrapper.class)

                if (wrapper != null) {
                    def action = new M2ReleaseAction(project, false, false, false)
                    def values = []
                    values.add(new StringParameterDefinition(M2ReleaseBuildWrapper.DescriptorImpl.DEFAULT_RELEASE_VERSION_ENVVAR, action.computeReleaseVersion(), ""))
                    values.add(new StringParameterDefinition(M2ReleaseBuildWrapper.DescriptorImpl.DEFAULT_DEV_VERSION_ENVVAR, action.computeNextVersion(), ""))
                    values.add(new BooleanParameterDefinition(M2ReleaseBuildWrapper.DescriptorImpl.DEFAULT_DRYRUN_ENVVAR, false, ""))

                    table(width: '100%', class: 'parameters') {
                        for (parameterDefinition in values) {
                            tbody {
                                st.include(it: parameterDefinition, page: parameterDefinition.descriptor.valuePage)
                            }
                        }
                    }
                } else {
                    text("${project.fullDisplayName} doesn't have m2 release plugin configuration")
                }
            } else {
                text("${project.fullDisplayName} is of wrong type and can't be released: ${project.class.simpleName}")
            }
        } else {
            text("no such job ${jobName}")
        }
    } else {
        text('no job specified')
    }
}
