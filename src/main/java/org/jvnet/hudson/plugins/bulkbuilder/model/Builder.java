/*
 * The MIT License
 *
 * Copyright (c) 2010-2011 Simon Westcott
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.plugins.bulkbuilder.model;

import hudson.Util;
import hudson.model.BooleanParameterDefinition;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Predicate;

/**
 * @author simon
 */
public class Builder {

    private static final Logger LOGGER = Logger.getLogger(Builder.class
	    .getName());

    /**
     * Key/value map of user parameters
     */
    private Map<String, String> userParams;

    public Builder() {
    }

    /**
     * Create new Builder object, passing in any project parameters
     * 
     * @param userParams
     */
    public Builder(Map<String, String> userParams) {
	this.userParams = userParams;
    }

    /**
     * Build all Jenkins projects
     */
    public final int buildAll() {
	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Starting to build all jobs.");
	}

	int i = 0;

	for (AbstractProject<?, ?> project : getProjects()) {
	    doBuildProject(project);
	    i++;
	}

	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Finished building all jobs.");
	}

	return i;
    }

    /**
     * Build all Jenkins projects
     */
    public final int build(Predicate<AbstractBuild<?, ?>> pred) {
	int i = 0;

	for (AbstractProject<?, ?> project : getProjects()) {
	    AbstractBuild<?, ?> build = project.getLastCompletedBuild();

	    if (pred.apply(build)) {

		if (LOGGER.isLoggable(Level.FINE)) {
		    LOGGER.log(Level.FINE, "Scheduling build for job: {0}",
			    project.getDisplayName());
		}

		doBuildProject(project);
		i++;
	    }
	}

	return i;

    }

    private int buildWorseOrEqualsTo(final Result r) {
	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Starting to build " + r.toString()
		    + " jobs.");
	}

	int i = build(new Predicate<AbstractBuild<?, ?>>() {
	    public boolean apply(AbstractBuild<?, ?> build) {
		return build == null || build.getResult().isWorseOrEqualTo(r);
	    }
	});

	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Finished building " + r.toString()
		    + " jobs.");
	}

	return i;
    }

    private int buildExactStatus(final Result r) {
	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Starting to build " + r.toString()
		    + " jobs.");
	}

	int i = build(new Predicate<AbstractBuild<?, ?>>() {
	    public boolean apply(AbstractBuild<?, ?> build) {
		return build == null || build.getResult() == r;
	    }
	});

	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Finished building " + r.toString()
		    + " jobs.");
	}

	return i;
    }

    /**
     * Build all unstable builds.
     * 
     * This includes projects that are unstable, have not been built before,
     * failed and aborted projects.
     */
    public final int buildUnstable() {
	return buildWorseOrEqualsTo(Result.UNSTABLE);
    }

    /**
     * Build all unstable builds only.
     */
    public final int buildUnstableOnly() {
	return buildExactStatus(Result.UNSTABLE);
    }

    /**
     * Build failed Jenkins projects.
     * 
     * This includes projects that have not been built before and failed and
     * aborted projects.
     */
    public final int buildFailed() {
	return buildWorseOrEqualsTo(Result.FAILURE);
    }

    /**
     * Build all failed builds only.
     */
    public int buildFailedOnly() {
	return buildExactStatus(Result.FAILURE);
    }

    /**
     * Build all not built jobs.
     * 
     * This includes projects that are have not been built before and aborted
     * projects.
     */
    public int buildNotBuilt() {
	return buildWorseOrEqualsTo(Result.NOT_BUILT);
    }

    /**
     * Build all not built jobs only.
     */
    public int buildNotBuildOnly() {
	return buildExactStatus(Result.NOT_BUILT);
    }

    /**
     * Build all aborted builds.
     */
    public int buildAborted() {
	return buildWorseOrEqualsTo(Result.ABORTED);
    }

    /**
     * Build projects that matched the supplied pattern
     * 
     * @param pattern
     */
    public final int buildPattern(String pattern) {
	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE,
		    "Starting to build jobs matching pattern, '{0}'.", pattern);
	}

	int i = 0;

	for (AbstractProject<?, ?> project : getProjects()) {
	    if (project.getDisplayName().contains(pattern)) {
		doBuildProject(project);
		i++;
	    }
	}

	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Finished building jobs matching pattern.");
	}

	return i;
    }

    /**
     * Build projects that matched the supplied pattern
     * 
     * @param pattern
     */
    public final int buildView(String viewName) {
	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Starting to build jobs in View '"
		    + viewName + "'.");
	}

	int i = 0;

	View view = Hudson.getInstance().getView(viewName);

	if (view == null) {
	    if (LOGGER.isLoggable(Level.WARNING)) {
		LOGGER.log(Level.WARNING, "View '" + viewName + "' not found!");
	    }
	}

	else {
	    for (AbstractProject<?, ?> project : Util.createSubList(
		    view.getItems(), AbstractProject.class)) {
		doBuildProject(project);
		i++;
	    }
	}

	if (LOGGER.isLoggable(Level.FINE)) {
	    LOGGER.log(Level.FINE, "Finished building jobs matching view.");
	}

	return i;
    }

    /**
     * Return a list of projects which can be built
     * 
     * @return
     */
    protected final List<AbstractProject<?, ?>> getProjects() {

	List<AbstractProject<?, ?>> projects = new ArrayList<AbstractProject<?, ?>>();

	for (TopLevelItem topLevelItem : Hudson.getInstance().getItems()) {
	    if (!(topLevelItem instanceof AbstractProject)) {
		continue;
	    }

	    AbstractProject<?, ?> project = (AbstractProject<?, ?>) topLevelItem;
	    if (!project.isBuildable()) {
		continue;
	    }

	    projects.add(project);
	}

	return projects;
    }

    /**
     * Actually build a project, passing in parameters where appropriate
     * 
     * @param project
     * @return
     */
    protected final void doBuildProject(AbstractProject<?, ?> project) {
	if (!project.hasPermission(AbstractProject.BUILD)) {
	    if (LOGGER.isLoggable(Level.WARNING)) {
		LOGGER.log(Level.WARNING,
			"Insufficient permissions to build {0}",
			project.getName());
	    }
	    return;
	}

	// no user parameters provided, just build it
	if (userParams == null) {
	    project.scheduleBuild(new Cause.UserCause());
	    return;
	}

	ParametersDefinitionProperty pp = (ParametersDefinitionProperty) project
		.getProperty(ParametersDefinitionProperty.class);

	// project does not except any parameters, just build it
	if (pp == null) {
	    project.scheduleBuild(new Cause.UserCause());
	    return;
	}

	List<ParameterDefinition> parameterDefinitions = pp
		.getParameterDefinitions();
	List<ParameterValue> values = new ArrayList<ParameterValue>();

	for (ParameterDefinition paramDef : parameterDefinitions) {

		ParameterValue value;
		if(paramDef instanceof StringParameterDefinition){
			StringParameterDefinition stringParamDef = (StringParameterDefinition) paramDef;
			if( userParams.containsKey( paramDef.getName() ) )
			{
				value = stringParamDef.createValue( userParams.get( stringParamDef.getName() ) );
			}
			else
			{
				// No, then use the default value
				value = stringParamDef.createValue( stringParamDef.getDefaultValue() );
			}
		}
		else if(paramDef instanceof ChoiceParameterDefinition ){
			ChoiceParameterDefinition choiceParaDef = (ChoiceParameterDefinition)paramDef;
			if( this.userParams.containsKey( paramDef.getName() ) )
			{
				value = choiceParaDef.createValue( this.userParams.get( choiceParaDef.getName() ) );
			}
			else
			{
				value = choiceParaDef.createValue( choiceParaDef.getDefaultParameterValue().value );
			}
		}
		else if(paramDef instanceof BooleanParameterDefinition ){
			BooleanParameterDefinition boolParaDef = (BooleanParameterDefinition) paramDef;
			if( this.userParams.containsKey( paramDef.getName() ) )
			{
				value = boolParaDef.createValue( this.userParams.get( boolParaDef.getName() ) );
			}
			else
			{
				value = boolParaDef.getDefaultParameterValue();
			}
		}
		else
		{
			value = paramDef.getDefaultParameterValue();
		}
		LOGGER.log(Level.INFO, "Parameter Value = " + value.toString());
		values.add(value);
	}

	// project.scheduleBuild(1, new Cause.UserCause(), new
	// ParametersAction(values));
	Hudson.getInstance().getQueue()
		.schedule(pp.getOwner(), 1, new ParametersAction(values));
    }

}
