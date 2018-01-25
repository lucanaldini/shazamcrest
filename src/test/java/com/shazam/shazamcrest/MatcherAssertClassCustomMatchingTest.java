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
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.Test;

import static com.shazam.shazamcrest.model.ChildBean.Builder.child;
import static com.shazam.shazamcrest.model.ParentBean.Builder.parent;
import static com.shazam.shazamcrest.util.AssertionHelper.assertThat;
import static com.shazam.shazamcrest.util.AssertionHelper.sameBeanAs;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests which verify the possibility to match beans applying hamcrest matchers on class types.
 */
public class MatcherAssertClassCustomMatchingTest {

	@Test
	public void succeedsWhenBeanDiffersButSatisfyCustomMatcher() {
		ParentBean.Builder expected = parent().parentString("same").childBean(child().childString("apple"));
		ParentBean.Builder actual = parent().parentString("same").childBean(child().childString("banana"));
		
		assertThat(actual, sameBeanAs(expected).with(ChildBean.class, notEmptyChildString()));
	}

	@Test(expected = AssertionError.class)
	public void failsWhenCustomMatchingIsUsedButOtherDifferencesArePresent() {
		ParentBean.Builder expected = parent().parentString("same").childBean(child().childString("apple"));
		ParentBean.Builder actual = parent().parentString("not-same").childBean(child().childString("banana"));

		assertThat(actual, sameBeanAs(expected).with(ChildBean.class, notEmptyChildString()));
	}

	@Test(expected = AssertionError.class)
	public void failsWhenCustomMatcherDoesNotMatch() {
		ParentBean.Builder expected = parent().parentString("same").childBean(child().childString("apple"));
		ParentBean.Builder actual = parent().parentString("same").childBean(child().childString("banana"));

		assertThat(actual, sameBeanAs(expected).with(ChildBean.class, nullValue(ChildBean.class)));
	}

	@Test
	public void succeedsWhenActualMatchesCustomMatcherButExpectedDoesNot() {
		ParentBean.Builder expected = parent().parentString("same").childBean(child().childString("apple"));
		ParentBean.Builder actual = parent().parentString("same");

		assertThat(actual, sameBeanAs(expected).with(ChildBean.class, nullValue(ChildBean.class)));
	}

	@Test(expected = AssertionError.class)
	public void failsWhenNotAllTypesOccurrencesSatisfyCustomMatcher() {
		ParentBean.Builder expected = parent().parentString("same").addToChildBeanList(child());
		ParentBean.Builder actual = parent().parentString("same").addToChildBeanList(child().childString("non-empty")).childBean(child().childString(""));

		assertThat(actual, sameBeanAs(expected).with(ChildBean.class, notEmptyChildString()));
	}

	@Test
	public void succeedsWhenActualIsNullAndCustomMatcherExpectsNull() {
		MatcherAssert.assertThat(null, sameBeanAs(parent()).with(ParentBean.class, nullValue(ParentBean.class)));
	}

	private Matcher<ChildBean> notEmptyChildString() {
		return new FeatureMatcher<ChildBean, String>(not(isEmptyString()), "not empty", "childString") {
			@Override
			protected String featureValueOf(ChildBean o) {
				return o.getChildString();
			}
		};
	}
}
