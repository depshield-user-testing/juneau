// ***************************************************************************************************************************
// * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file *
// * distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file        *
// * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance            *
// * with the License.  You may obtain a copy of the License at                                                              *
// *                                                                                                                         *
// *  http://www.apache.org/licenses/LICENSE-2.0                                                                             *
// *                                                                                                                         *
// * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an  *
// * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the        *
// * specific language governing permissions and limitations under the License.                                              *
// ***************************************************************************************************************************
package org.apache.juneau.microservice;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.logging.*;

import org.apache.juneau.config.*;
import org.apache.juneau.config.store.*;
import org.apache.juneau.config.vars.*;
import org.apache.juneau.microservice.console.*;
import org.apache.juneau.svl.*;
import org.apache.juneau.utils.*;

/**
 * Builder for {@link Microservice} class.
 *
 * <p>
 * Instances of this class are created using {@link Microservice#create()}.
 */
public class MicroserviceBuilder {

	Args args;
	ManifestFile manifest;
	Logger logger;
	LogConfig logConfig;
	Config config;
	String configName;
	ConfigStore configStore;
	ConfigBuilder configBuilder = Config.create();
	Boolean consoleEnabled;
	List<ConsoleCommand> consoleCommands = new ArrayList<>();
	VarResolverBuilder varResolverBuilder = VarResolver.create().defaultVars().vars(ConfigVar.class);
	Scanner consoleReader;
	PrintWriter consoleWriter;
	MicroserviceListener listener;

	/**
	 * Constructor.
	 */
	protected MicroserviceBuilder() {}

	/**
	 * Copy constructor.
	 *
	 * @param copyFrom The builder to copy settings from.
	 */
	protected MicroserviceBuilder(MicroserviceBuilder copyFrom) {
		this.args = copyFrom.args;
		this.manifest = copyFrom.manifest;
		this.logger = copyFrom.logger;
		this.configName = copyFrom.configName;
		this.logConfig = copyFrom.logConfig == null ? null : copyFrom.logConfig.copy();
		this.consoleEnabled = copyFrom.consoleEnabled;
		this.configBuilder = copyFrom.configBuilder;
		this.varResolverBuilder = copyFrom.varResolverBuilder;
		this.consoleReader = copyFrom.consoleReader;
		this.consoleWriter = copyFrom.consoleWriter;
	}

	/**
	 * Creates a copy of this builder.
	 *
	 * @return A new copy of this builder.
	 */
	public MicroserviceBuilder copy() {
		return new MicroserviceBuilder(this);
	}

	/**
	 * Instantiate a new microservice using the settings defined on this builder.
	 *
	 * @return A new microservice.
	 * @throws Exception
	 */
	public Microservice build() throws Exception {
		return new Microservice(this);
	}

	/**
	 * Specifies the command-line arguments passed into the Java command.
	 *
	 * <p>
	 * This is required if you use {@link Microservice#getArgs()} or <code>$ARG</code> string variables.
	 *
	 * @param args
	 * 	The command-line arguments passed into the Java command as a pre-parsed {@link Args} object.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder args(Args args) {
		this.args = args;
		return this;
	}

	/**
	 * Specifies the command-line arguments passed into the Java command.
	 *
	 * <p>
	 * This is required if you use {@link Microservice#getArgs()} or <code>$ARG</code> string variables.
	 *
	 * @param args
	 * 	The command-line arguments passed into the Java command as the raw command-line arguments.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder args(String...args) {
		this.args = new Args(args);
		return this;
	}

	/**
	 * Specifies the manifest file of the jar file this microservice is contained within.
	 *
	 * <p>
	 * This is required if you use {@link Microservice#getManifest()}.
	 * It's also used to locate initialization values such as <code>Main-Config</code>.
	 *
	 * <p>
	 * If you do not specify the manifest file, we attempt to resolve it through the following methods:
	 * <ol class='spaced-list'>
	 * 	<li>
	 * 		Looking on the file system for a file at <js>"META-INF/MANIFEST.MF"</js>.
	 * 		This is primarily to allow for running microservices from within eclipse workspaces where the manifest file
	 * 		is located in the project root.
	 * 	<li>
	 * 		Using the class loader for this class to find the file at the URL <js>"META-INF/MANIFEST.MF"</js>.
	 * </ol>
	 *
	 * @param value
	 * 	The manifest file of this microservice.
	 * 	<br>Can be any of the following types:
	 * 	<ul>
	 * 		<li>{@link ManifestFile}
	 * 		<li>{@link Manifest}
	 * 		<li>{@link Reader} - Containing the raw contents of the manifest.  Note that the input must end with a newline.
	 * 		<li>{@link InputStream} - Containing the raw contents of the manifest.  Note that the input must end with a newline.
	 * 		<li>{@link File} - File containing the raw contents of the manifest.
	 * 		<li>{@link String} - Path to file containing the raw contents of the manifest.
	 * 		<li>{@link Class} - Finds and loads the manifest file of the jar file that the specified class is contained within.
	 * 	</ul>
	 * @return This object (for method chaining).
	 * @throws IOException
	 */
	public MicroserviceBuilder manifest(Object value) throws IOException {
		if (value == null)
			this.manifest = null;
		else if (value instanceof ManifestFile)
			this.manifest = (ManifestFile)value;
		else if (value instanceof Manifest)
			this.manifest = new ManifestFile((Manifest)value);
		else if (value instanceof Reader)
			this.manifest = new ManifestFile((Reader)value);
		else if (value instanceof InputStream)
			this.manifest = new ManifestFile((InputStream)value);
		else if (value instanceof File)
			this.manifest = new ManifestFile((File)value);
		else if (value instanceof String)
			this.manifest = new ManifestFile(new File((String)value));
		else if (value instanceof Class)
			this.manifest = new ManifestFile((Class<?>)value);
		else
			throw new RuntimeException("Invalid type passed to MicroserviceBuilder.manifest(Object).  Type=["+value.getClass().getName()+"]");

		return this;
	}

	/**
	 * Specifies the logger used by the microservice and returned by the {@link Microservice#getLogger()} method.
	 *
	 * <p>
	 * Calling this method overrides the default logging mechanism controlled by the {@link #logConfig(LogConfig)} method.
	 *
	 * @param logger The logger to use for logging microservice messages.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder logger(Logger logger) {
		this.logger = logger;
		return this;
	}

	/**
	 * Specifies logging instructions for the microservice.
	 *
	 * <p>
	 * If not specified, the values are taken from the <js>"Logging"</js> section of the configuration.
	 *
	 * <p>
	 * This method is ignored if {@link #logger(Logger)} is used to set the microservice logger.
	 *
	 * @param logConfig The log configuration.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder logConfig(LogConfig logConfig) {
		this.logConfig = logConfig;
		return this;
	}

	/**
	 * Specifies the config for initializing this microservice.
	 *
	 * <p>
	 * Calling this method overrides the default configuration controlled by the {@link #configName(String)} and {@link #configStore(ConfigStore)} methods.
	 *
	 * @param config The configuration.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder config(Config config) {
		this.config = config;
		return this;
	}

	/**
	 * Specifies the config name for initializing this microservice.
	 *
	 * <p>
	 * By default, we use a {@link ConfigFileStore} store for configuration files.
	 * If the store is not overridden, then the config name represents the path of the configuration file relative to the
	 * JVM working directory.
	 *
	 * <p>
	 * If you do not specify the config file location, we attempt to resolve it through the following methods:
	 * <ol class='spaced-list'>
	 * 	<li>
	 * 		The <js>"configFile"</js> argument in the command line arguments passed in through the constructor.
	 * 	<li>
	 * 		The value of the <code>Main-Config</code> entry in the manifest file.
	 * 	<li>
	 * 		A config file in the same location and with the same name as the executable jar file.
	 * 		(e.g. <js>"java -jar myjar.jar"</js> will look for <js>"myjar.cfg"</js>).
	 * 	<li>
	 * 		Any other <js>"*.cfg"</js> file that can be found in the working directory.
	 * </ol>
	 *
	 * @param configName The configuration name.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder configName(String configName) {
		this.configName = configName;
		return this;
	}

	/**
	 * Specifies the config store to use for storing and retrieving configurations.
	 *
	 * <p>
	 * By default, we use a {@link ConfigFileStore} store for configuration files.
	 *
	 * @param configStore The configuration name.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder configStore(ConfigStore configStore) {
		this.configStore = configStore;
		return this;
	}

	/**
	 * Specifies that the Java console is enabled for this microservice.
	 *
	 * <p>
	 * If not specified, this value is taken from the <js>"Console/enabled"</js> configuration setting.
	 * If not specified in the configuration, defaults to <jk>false</jk>.
	 *
	 * @param consoleEnabled <jk>true</jk> if the Java console is enabled for this microservice.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder consoleEnabled(boolean consoleEnabled) {
		this.consoleEnabled = consoleEnabled;
		return this;
	}

	/**
	 * Specifies console commands to make available on the Java console.
	 *
	 * <p>
	 * Note that these are ignored if the console is not enabled via {@link #consoleEnabled(boolean)}.
	 *
	 * <p>
	 * This list augments the commands defined via the <js>"Console/commands"</js> configuration setting.
	 *
	 * <p>
	 * This method can only be used on console commands with no-arg constructors.
	 *
	 * @param consoleCommands The list of console commands to append to the list of available commands.
	 * @return This object (for method chaining).
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	@SuppressWarnings("unchecked")
	public MicroserviceBuilder consoleCommands(Class<? extends ConsoleCommand>...consoleCommands) throws InstantiationException, IllegalAccessException {
		for (Class<? extends ConsoleCommand> cc : consoleCommands)
			this.consoleCommands.add(cc.newInstance());
		return this;
	}

	/**
	 * Specifies console commands to make available on the Java console.
	 *
	 * <p>
	 * Note that these are ignored if the console is not enabled via {@link #consoleEnabled(boolean)}.
	 *
	 * <p>
	 * This list augments the commands defined via the <js>"Console/commands"</js> configuration setting.
	 *
	 * @param consoleCommands The list of console commands to append to the list of available commands.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder consoleCommands(ConsoleCommand...consoleCommands) {
		this.consoleCommands.addAll(Arrays.asList(consoleCommands));
		return this;
	}

	/**
	 * Specifies the console input and output.
	 *
	 * <p>
	 * If not specified, uses the console returned by {@link System#console()}.
	 * If that is not available, uses {@link System#in} and {@link System#out}.
	 *
	 * <p>
	 * Note that these are ignored if the console is not enabled via {@link #consoleEnabled(boolean)}.
	 *
	 * @param consoleReader The console input.
	 * @param consoleWriter The console output.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder console(Scanner consoleReader, PrintWriter consoleWriter) {
		this.consoleReader = consoleReader;
		this.consoleWriter = consoleWriter;
		return this;
	}

	/**
	 * Augments the set of variables defined in the configuration and var resolver.
	 *
	 * <p>
	 * This calls {@link VarResolverBuilder#vars(Class[])} on the var resolver used to construct the configuration
	 * object returned by {@link Microservice#getConfig()} and the var resolver returned by {@link Microservice#getVarResolver()}.
	 *
	 * @param vars The set of variables to append to the var resolver builder.
	 * @return This object (for method chaining).
	 */
	@SuppressWarnings("unchecked")
	public MicroserviceBuilder vars(Class<? extends Var>...vars) {
		varResolverBuilder.vars(vars);
		return this;
	}

	/**
	 * Adds a var resolver context object for vars defined in the configuration and var resolver.
	 *
	 * <p>
	 * This calls {@link VarResolverBuilder#contextObject(String,Object)} on the var resolver used to construct the configuration
	 * object returned by {@link Microservice#getConfig()} and the var resolver returned by {@link Microservice#getVarResolver()}.
	 *
	 * @param name The context object name.
	 * @param object The context object.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder varContext(String name, Object object) {
		varResolverBuilder.contextObject(name, object);
		return this;
	}

	/**
	 * Registers an event listener for this microservice.
	 *
	 * @param listener An event listener for this microservice.
	 * @return This object (for method chaining).
	 */
	public MicroserviceBuilder listener(MicroserviceListener listener) {
		this.listener = listener;
		return this;
	}
}
