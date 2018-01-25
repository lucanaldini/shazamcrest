/*
 * Copyright 2013 Shazam Entertainment Limited
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * 
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
*/
package com.shazam.shazamcrest;

import com.shazam.shazamcrest.model.ChildBean;
import com.shazam.shazamcrest.model.ParentBean;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import static com.shazam.shazamcrest.matchers.ChildBeanMatchers.childStringEqualTo;
import static com.shazam.shazamcrest.model.ChildBean.Builder.child;
import static com.shazam.shazamcrest.model.ParentBean.Builder.parent;
import static com.shazam.shazamcrest.util.AssertionHelper.assertThat;
import static com.shazam.shazamcrest.util.AssertionHelper.sameBeanAs;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.fail;

/**
 * Tests which verify the diagnostic displayed when a class custom matcher fails.
 */
public class MatcherAssertClassCustomMatchingDiagnosticTest {

	@Test
	public void includesDescriptionAndMismatchDescriptionForFailingMatcherOnPrimitiveField() {
		ParentBean.Builder expected = parent().parentString("kiwi").childBean(child().childString("apple"));
		ParentBean.Builder actual = parent().parentString("kiwi").childBean(child().childString("banana"));
		
		try {
			assertThat(actual, sameBeanAs(expected).with(String.class, equalTo("kiwi")));
			fail("Expected assertion error");
		} catch (AssertionError e) {
			MatcherAssert.assertThat(e.getMessage(), endsWith("and String \"kiwi\"\n     but: String was \"banana\""));
		}
	}

	@Test
	public void includesJsonSnippetOfNonPrimitiveFieldOnMatchFailure() {
		ParentBean.Builder expected = parent().parentString("kiwi").childBean(child().childString("apple"));
		ParentBean.Builder actual = parent().parentString("kiwi").childBean(child().childString("banana").childInteger(1));

		try {
			assertThat(actual, sameBeanAs(expected).with(ChildBean.class, childStringEqualTo("kiwi")));
			fail("Expected assertion error");
		} catch (AssertionError e) {
			MatcherAssert.assertThat(e.getMessage(), endsWith("and ChildBean having string field \"kiwi\"\n     but: ChildBean string field was \"banana\"\n{\n  \"childString\": \"banana\",\n  \"childInteger\": 1\n}"));
		}
	}

	@Test
	public void doesNotIncludeJsonSnippetOnNullField() {
		ParentBean.Builder expected = parent().childBean(child().childString("apple"));
		ParentBean.Builder actual = parent();

		try {
			assertThat(actual, sameBeanAs(expected).with(ChildBean.class, childStringEqualTo("kiwi")));
			fail("Expected assertion error");
		} catch (AssertionError e) {
			MatcherAssert.assertThat(e.getMessage(), endsWith("and ChildBean having string field \"kiwi\"\n     but: ChildBean was null"));
		}
	}
}
