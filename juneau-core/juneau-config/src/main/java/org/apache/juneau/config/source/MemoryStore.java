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
package org.apache.juneau.config.source;

import static org.apache.juneau.internal.StringUtils.*;

import java.io.*;
import java.util.concurrent.*;

import org.apache.juneau.*;

/**
 * Filesystem-based storage location for configuration files.
 * 
 * <p>
 * Points to a file system directory containing configuration files.
 */
public class MemoryStore extends Store {

	/**
	 * Create a new builder for this object.
	 * 
	 * @return A new builder for this object.
	 */
	public static MemoryStoreBuilder create() {
		return new MemoryStoreBuilder();
	}
	
	@Override /* Context */
	public MemoryStoreBuilder builder() {
		return new MemoryStoreBuilder(getPropertyStore());
	}

	private final ConcurrentHashMap<String,String> cache = new ConcurrentHashMap<>();
	
	/**
	 * Constructor.
	 * 
	 * @param ps The settings for this content store.
	 */
	protected MemoryStore(PropertyStore ps) {
		super(ps);
	}
	
	@Override /* Store */
	public synchronized String read(String name) throws Exception {
		return cache.get(name);
	}

	@Override /* Store */
	public synchronized boolean write(String name, String oldContents, String newContents) throws Exception {
		String s = cache.get(name);
		
		if (! isEquals(s, oldContents)) 
			return false;
		
		if (! isEquals(s, newContents)) {
			cache.put(name, newContents);
			onChange(name, newContents);
		}
		
		return true;
	}

	/**
	 * No-op.
	 */
	@Override /* Closeable */
	public void close() throws IOException {
		// No-op
	}
}