/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling;

import org.gradle.tooling.model.Model;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@code ModelBuilder} allows you to fetch a snapshot of the model for a project.
 * Instances of {@code ModelBuilder} are not thread-safe.
 * <p>
 * You use a {@code ModelBuilder} as follows:
 *
 * <ul>
 * <li>Create an instance of {@code ModelBuilder} by calling {@link org.gradle.tooling.ProjectConnection#model(Class)}.
 * <li>Configure the builder as appropriate.
 * <li>Call either {@link #get()} or {@link #get(ResultHandler)} to build the model.
 * <li>Optionally, you can reuse the builder to build the model multiple times.
 * </ul>
 *
 * Example:
 * <pre autoTested=''>
 * ProjectConnection connection = GradleConnector.newConnector()
 *    .forProjectDirectory(new File("someFolder"))
 *    .connect();
 *
 * try {
 *    ModelBuilder&lt;GradleProject&gt; builder = connection.model(GradleProject.class);
 *
 *    //if you use a different than usual build file name:
 *    builder.withArguments("--build-file", "theBuild.gradle");
 *
 *    //configure the standard input in case your build is interactive:
 *    builder.setStandardInput(new ByteArrayInputStream("consume this!".getBytes()));
 *
 *    //if you want to listen to the progress events:
 *    ProgressListener listener = null; // use your implementation
 *    builder.addProgressListener(listener);
 *
 *    //get the model:
 *    GradleProject project = builder.get();
 *
 *    //query the model for information:
 *    System.out.println("Available tasks: " + project.getTasks());
 * } finally {
 *    connection.close();
 * }
 * </pre>
 *
 * @param <T> The type of model to build
 */
public interface ModelBuilder<T extends Model> extends LongRunningOperation {

    /**
     * {@inheritDoc}
     */
    ModelBuilder<T> withArguments(String ... arguments);

    /**
     * {@inheritDoc}
     */
    ModelBuilder<T> setStandardOutput(OutputStream outputStream);

    /**
     * {@inheritDoc}
     */
    ModelBuilder<T> setStandardError(OutputStream outputStream);

    /**
     * {@inheritDoc}
     */
    ModelBuilder<T> setStandardInput(InputStream inputStream);

    /**
     * {@inheritDoc}
     */
    ModelBuilder<T> setJavaHome(File javaHome);

    /**
     * {@inheritDoc}
     */
    ModelBuilder<T> setJvmArguments(String... jvmArguments);

    /**
     * {@inheritDoc}
     */
    ModelBuilder<T> addProgressListener(ProgressListener listener);

    /**
     * Fetch the model, blocking until it is available.
     *
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the features required to build this model.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     *          when you have configured the long running operation with a settings
     *          like: {@link #setStandardInput(java.io.InputStream)}, {@link #setJavaHome(java.io.File)},
     *          {@link #setJvmArguments(String...)} but those settings are not supported on the target Gradle.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    T get() throws GradleConnectionException;

    /**
     * Starts fetching the build. This method returns immediately, and the result is later passed to the given handler.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void get(ResultHandler<? super T> handler) throws IllegalStateException;
}
