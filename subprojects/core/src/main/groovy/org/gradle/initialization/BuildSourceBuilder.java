/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.initialization;

import org.gradle.BuildAdapter;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.internal.plugins.EmbeddableJavaProject;
import org.gradle.api.invocation.Gradle;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.internal.FileIntegrityViolationSuppressingPersistentStateCacheDecorator;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.OnDemandFileAccess;
import org.gradle.cache.internal.SimpleStateCache;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class BuildSourceBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildSourceBuilder.class);

    private final GradleLauncherFactory gradleLauncherFactory;
    private final ClassLoaderRegistry classLoaderRegistry;
    private FileLockManager fileLockManager;

    private static final String DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE = "defaultBuildSourceScript.txt";

    public BuildSourceBuilder(GradleLauncherFactory gradleLauncherFactory, ClassLoaderRegistry classLoaderRegistry, FileLockManager fileLockManager) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.classLoaderRegistry = classLoaderRegistry;
        this.fileLockManager = fileLockManager;
    }

    public URLClassLoader buildAndCreateClassLoader(StartParameter startParameter) {
        ClassPath classpath = createBuildSourceClasspath(startParameter);
        return new URLClassLoader(classpath.getAsURLArray(), classLoaderRegistry.getRootClassLoader());
    }

    private ClassPath createBuildSourceClasspath(StartParameter startParameter) {
        assert startParameter.getCurrentDir() != null && startParameter.getBuildFile() == null;

        LOGGER.debug("Starting to build the build sources.");
        if (!startParameter.getCurrentDir().isDirectory()) {
            LOGGER.debug("Gradle source dir does not exist. We leave.");
            return new DefaultClassPath();
        }
        LOGGER.info("================================================" + " Start building buildSrc");
        final StartParameter startParameterArg = startParameter.newInstance();
        startParameterArg.setProjectProperties(startParameter.getProjectProperties());
        startParameterArg.setSearchUpwards(false);
        startParameterArg.setProfile(startParameter.isProfile());

        File buildSrcCacheFile = new File(startParameter.getCurrentDir(), ".gradle/noVersion/buildSrc");
        PersistentStateCache<Boolean> stateCache = new FileIntegrityViolationSuppressingPersistentStateCacheDecorator<Boolean>(
                new SimpleStateCache<Boolean>(
                        buildSrcCacheFile,
                        new OnDemandFileAccess(
                                buildSrcCacheFile,
                                "buildSrc rebuild status cache",
                                fileLockManager),
                        new DefaultSerializer<Boolean>())
        );

        final Set<File> buildSourceClasspath = new LinkedHashSet<File>();
        stateCache.update(new PersistentStateCache.UpdateAction<Boolean>() {
            public Boolean update(Boolean oldValue) {
                GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(startParameterArg);
                BuildSrcBuildListener listener = new BuildSrcBuildListener(oldValue == null);
                gradleLauncher.addListener(listener);
                gradleLauncher.run().rethrowFailure();
                buildSourceClasspath.addAll(listener.getRuntimeClasspath());
                return true;
            }
        });

        LOGGER.debug("Gradle source classpath is: {}", buildSourceClasspath);
        LOGGER.info("================================================" + " Finished building buildSrc");

        return new DefaultClassPath(buildSourceClasspath);
    }

    static URL getDefaultScript() {
        return BuildSourceBuilder.class.getResource(DEFAULT_BUILD_SOURCE_SCRIPT_RESOURCE);
    }

    private static class BuildSrcBuildListener extends BuildAdapter {
        private EmbeddableJavaProject projectInfo;
        private Set<File> classpath;
        private final boolean rebuild;

        public BuildSrcBuildListener(boolean rebuild) {
            this.rebuild = rebuild;
        }

        @Override
        public void projectsLoaded(Gradle gradle) {
            gradle.getRootProject().apply(WrapUtil.toMap("from", getDefaultScript()));
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            projectInfo = gradle.getRootProject().getConvention().getPlugin(EmbeddableJavaProject.class);
            gradle.getStartParameter().setTaskNames(rebuild ? projectInfo.getRebuildTasks() : projectInfo.getBuildTasks());
            classpath = projectInfo.getRuntimeClasspath().getFiles();
        }

        public Collection<File> getRuntimeClasspath() {
            return classpath;
        }
    }
}
