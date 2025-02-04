/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.util.version;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import net.fabricmc.loader.api.VersionParsingException;

public final class SemanticVersionPredicateParser {
	private static final Map<String, Function<FabricSemanticVersionImpl, Predicate<FabricSemanticVersionImpl>>> PREFIXES;

	public static Predicate<FabricSemanticVersionImpl> create(String text) throws VersionParsingException {
		List<Predicate<FabricSemanticVersionImpl>> predicateList = new ArrayList<>();
		List<FabricSemanticVersionImpl> prereleaseVersions = new ArrayList<>();

		for (String s : text.split(" ")) {
			s = s.trim();
			if (s.isEmpty() || s.equals("*")) {
				continue;
			}

			Function<FabricSemanticVersionImpl, Predicate<FabricSemanticVersionImpl>> factory = null;
			for (String prefix : PREFIXES.keySet()) {
				if (s.startsWith(prefix)) {
					factory = PREFIXES.get(prefix);
					s = s.substring(prefix.length());
					break;
				}
			}

			FabricSemanticVersionImpl version = new FabricSemanticVersionImpl(s, true);
			if (version.isPrerelease()) {
				prereleaseVersions.add(version);
			}

			if (factory == null) {
				factory = PREFIXES.get("=");
			} else if (version.hasWildcard()) {

				// A warning:
				//	In a wonderful twist of events, FabricSemanticVersionImpl.matches(Version)'s behavior does not match
				//	the same behavior as taking the parsed predicates and using them as toString.
				//	The behavior is ****almost**** identical, EXCEPT that this code doesn't allow
				//	=1.16.x to be valid, just 1.16.x, even though the rest of the logic treats them as exactly the same.
				//	Using the parsed predicate erases this distinction, and causes a bug where the version range 1.16.x
				//	can never be matched.
				//	To fix this, I've removed the exception that used to be here.
				// 	This allows technically illegal fabric.mod.jsons through our parsers, but too bad! - Glitch
			}

			predicateList.add(factory.apply(version));
		}

		if (predicateList.isEmpty()) {
			return (s) -> true;
		}

		return (s) -> {
			for (Predicate<FabricSemanticVersionImpl> p : predicateList) {
				if (!p.test(s)) {
					return false;
				}
			}

			return true;
		};
	}

	static {
		// Make sure to keep this sorted in order of length!
		PREFIXES = new LinkedHashMap<>();
		PREFIXES.put(">=", (target) -> (source) -> source.compareTo(target) >= 0);
		PREFIXES.put("<=", (target) -> (source) -> source.compareTo(target) <= 0);
		PREFIXES.put(">", (target) -> (source) -> source.compareTo(target) > 0);
		PREFIXES.put("<", (target) -> (source) -> source.compareTo(target) < 0);
		PREFIXES.put("=", (target) -> (source) -> source.compareTo(target) == 0);
		PREFIXES.put("~", (target) -> (source) -> source.compareTo(target) >= 0
				&& source.getVersionComponent(0) == target.getVersionComponent(0)
				&& source.getVersionComponent(1) == target.getVersionComponent(1));
		PREFIXES.put("^", (target) -> (source) -> source.compareTo(target) >= 0
				&& source.getVersionComponent(0) == target.getVersionComponent(0));
	}
}
