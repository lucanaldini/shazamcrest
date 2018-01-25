/*
 * Copyright 2013 Shazam Entertainment Limited
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * 
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.shazam.shazamcrest.matcher;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.shazam.shazamcrest.ComparisonDescription;
import org.hamcrest.Description;
import org.hamcrest.DiagnosingMatcher;
import org.hamcrest.Matcher;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.shazam.shazamcrest.BeanFinder.findBeanAt;
import static com.shazam.shazamcrest.CyclicReferenceDetector.getClassesWithCircularReferences;
import static com.shazam.shazamcrest.FieldsIgnorer.MARKER;
import static com.shazam.shazamcrest.FieldsIgnorer.findPaths;

/**
 * Extends the functionalities of {@link DiagnosingMatcher} with the possibility to specify fields and object types to
 * ignore in the comparison, or fields to be matched with a custom matcher
 */
class DiagnosingCustomisableMatcher<T> extends DiagnosingMatcher<T> implements CustomisableMatcher<T> {
	private final Set<String> pathsToIgnore = new HashSet<String>();
	private final Map<String, Matcher<?>> pathCustomMatchers = new HashMap<String, Matcher<?>>();
	protected final Map<Class<?>, Matcher<?>> classCustomMatchers = new HashMap<Class<?>, Matcher<?>>();
	protected final List<Class<?>> typesToIgnore = new ArrayList<Class<?>>();
	protected final List<Matcher<String>> patternsToIgnore = new ArrayList<Matcher<String>>();
    protected final Set<Class<?>> circularReferenceTypes = new HashSet<Class<?>>();
	protected final T expected;

    public DiagnosingCustomisableMatcher(T expected) {
        this.expected = expected;
    }

	@Override
	public void describeTo(Description description) {
		Gson gsonForExpected = new GsonProvider(typesToIgnore, patternsToIgnore, circularReferenceTypes, classCustomMatchers).gsonForExpected();
		description.appendText(filterJson(gsonForExpected, expected));
		for (String fieldPath : pathCustomMatchers.keySet()) {
			description.appendText("\nand ")
				.appendText(fieldPath).appendText(" ")
				.appendDescriptionOf(pathCustomMatchers.get(fieldPath));
		}
		for (Class<?> type : classCustomMatchers.keySet()) {
			description.appendText("\nand ")
				.appendText(type.getSimpleName()).appendText(" ")
				.appendDescriptionOf(classCustomMatchers.get(type));
		}
	}

	@Override
	protected boolean matches(Object actual, Description mismatchDescription) {
        circularReferenceTypes.addAll(getClassesWithCircularReferences(actual));
        circularReferenceTypes.addAll(getClassesWithCircularReferences(expected));
		Gson gsonForExpected = new GsonProvider(typesToIgnore, patternsToIgnore, circularReferenceTypes, classCustomMatchers).gsonForExpected();
		Gson gsonForActual = new GsonProvider(typesToIgnore, patternsToIgnore, circularReferenceTypes, classCustomMatchers).gsonForActual();

		if (!areCustomMatchersMatching(actual, mismatchDescription, gsonForActual)) {
			return false;
		}
		
		String expectedJson = filterJson(gsonForExpected, expected);

		if (actual == null) {
			if ("null".equals(expectedJson)) {
				return true;
			}

			return appendMismatchDescription(mismatchDescription, expectedJson, "null", "actual was null");
		}

		try {
			String actualJson = filterJson(gsonForActual, actual);

			return assertEquals(expectedJson, actualJson, mismatchDescription);
		} catch (CustomMatcherException e) {
			mismatchDescription.appendText(e.getClassSimpleName() + " ");
			e.getMatcher().describeMismatch(e.getObject(), mismatchDescription);
			if (e.getJsonSnippet() != null) {
				mismatchDescription.appendText("\n" + e.getJsonSnippet());
			}
			return false;
		}
	}

	private boolean areCustomMatchersMatching(Object actual, Description mismatchDescription, Gson gson) {
		Map<Object, Matcher<?>> customMatching = new HashMap<Object, Matcher<?>>();
		for (Entry<String, Matcher<?>> entry : pathCustomMatchers.entrySet()) {
			Object object = actual == null ? null : findBeanAt(entry.getKey(), actual);
			customMatching.put(object, pathCustomMatchers.get(entry.getKey()));
		}
		
		for (Entry<Object, Matcher<?>> entry : customMatching.entrySet()) {
			Matcher<?> matcher = entry.getValue();
			Object object = entry.getKey();
			if (!matcher.matches(object)) {
				appendFieldPath(matcher, mismatchDescription);
				matcher.describeMismatch(object, mismatchDescription);
				appendFieldJsonSnippet(object, mismatchDescription, gson);
				return false;
			}
		}
		return true;
	}

	@Override
	public CustomisableMatcher<T> ignoring(String fieldPath) {
		pathsToIgnore.add(fieldPath);
		return this;
	}

	@Override
	public CustomisableMatcher<T> ignoring(Class<?> clazz) {
		typesToIgnore.add(clazz);
		return this;
	}
	
	@Override
	public CustomisableMatcher<T> ignoring(Matcher<String> fieldNamePattern) {
	    patternsToIgnore.add(fieldNamePattern);
	    return this;
	}

    @Override
	public <V> CustomisableMatcher<T> with(String fieldPath, Matcher<V> matcher) {
		pathCustomMatchers.put(fieldPath, matcher);
		return this;
	}

	@Override
	public <V> CustomisableMatcher<T> with(Class<V> clazz, Matcher<V> matcher) {
		classCustomMatchers.put(clazz, matcher);
		return this;
	}

	protected boolean appendMismatchDescription(Description mismatchDescription, String expectedJson, String actualJson, String message) {
		if (mismatchDescription instanceof ComparisonDescription) {
			ComparisonDescription shazamMismatchDescription = (ComparisonDescription) mismatchDescription;
			shazamMismatchDescription.setComparisonFailure(true);
			shazamMismatchDescription.setExpected(expectedJson);
			shazamMismatchDescription.setActual(actualJson);
			shazamMismatchDescription.setDifferencesMessage(message);
		}
		mismatchDescription.appendText(message);
		return false;
	}

	private boolean assertEquals(final String expectedJson, String actualJson, Description mismatchDescription) {
		try {
			JSONAssert.assertEquals(expectedJson, actualJson, true);
		} catch (AssertionError e) {
			return appendMismatchDescription(mismatchDescription, expectedJson, actualJson, e.getMessage());
		} catch (JSONException e) {
			return appendMismatchDescription(mismatchDescription, expectedJson, actualJson, e.getMessage());
		}

		return true;
	}

	private void appendFieldJsonSnippet(Object actual, Description mismatchDescription, Gson gson) {
		JsonElement jsonTree = gson.toJsonTree(actual);
		if (!jsonTree.isJsonPrimitive() && !jsonTree.isJsonNull()) {
			mismatchDescription.appendText("\n" + gson.toJson(actual));
		}
	}
	
	private void appendFieldPath(Matcher<?> matcher, Description mismatchDescription) {
		for (Entry<String, Matcher<?>> entry : pathCustomMatchers.entrySet()) {
			if (entry.getValue().equals(matcher)) {
				mismatchDescription.appendText(entry.getKey()).appendText(" ");
			}
		}
	}

	private String filterJson(Gson gson, Object object) {
		Set<String> set = new HashSet<String>();
		set.addAll(pathsToIgnore);
		set.addAll(pathCustomMatchers.keySet());
		JsonElement filteredJson = findPaths(gson, object, set);

		return removeSetMarker(gson.toJson(filteredJson));
	}
	
	private String removeSetMarker(String json) {
		return json.replaceAll(MARKER, "");
	}
}