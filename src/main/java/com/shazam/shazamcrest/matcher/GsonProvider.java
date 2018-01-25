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

import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.graph.GraphAdapterBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.hamcrest.Matcher;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.collect.Sets.newTreeSet;
import static com.shazam.shazamcrest.FieldsIgnorer.MARKER;
import static org.apache.commons.lang3.ClassUtils.isPrimitiveOrWrapper;

/**
 * Provides an instance of {@link Gson}. If any class type has been ignored on the matcher, the {@link Gson} provided
 * will include an {@link ExclusionStrategy} which will skip the serialisation of fields for that type.
 */
class GsonProvider {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy hh:mm:ss.SSS aa");

	private final List<Class<?>> typesToIgnore;
	private final List<Matcher<String>> fieldsToIgnore;
	private final Set<Class<?>> circularReferenceTypes;
	private final Map<Class<?>, Matcher<?>> typesWithCustomMatchers;

	GsonProvider(List<Class<?>> typesToIgnore, List<Matcher<String>> fieldsToIgnore, Set<Class<?>> circularReferenceTypes, Map<Class<?>, Matcher<?>> typesWithCustomMatchers) {
		this.typesToIgnore = typesToIgnore;
		this.fieldsToIgnore = fieldsToIgnore;
		this.circularReferenceTypes = circularReferenceTypes;
		this.typesWithCustomMatchers = typesWithCustomMatchers;
	}

	/**
     * Returns a {@link Gson} instance containing {@link ExclusionStrategy} based on the object types to ignore during
     * serialisation.
     *
	 * @return an instance of {@link Gson}
     */
	Gson gsonForActual() {
		GsonBuilder gsonBuilder = initGsonBuilder();

		registerTypesWithCustomMatchersSerialisation(gsonBuilder, typesWithCustomMatchers);
		registerExclusionStrategies(gsonBuilder, typesToIgnore, fieldsToIgnore);

		return gsonBuilder.create();
	}

	Gson gsonForExpected() {
		GsonBuilder gsonBuilder = initGsonBuilder();

		registerExclusionStrategies(gsonBuilder, both(typesToIgnore, typesWithCustomMatchers.keySet()), fieldsToIgnore);

		return gsonBuilder.create();
    }

	private GsonBuilder initGsonBuilder() {
		final GsonBuilder gsonBuilder = initGson();

		registerCircularReferenceTypes(circularReferenceTypes, gsonBuilder);

		registerGuavaOptionalSerialisation(gsonBuilder);
		registerSetSerialisation(gsonBuilder);
		registerMapSerialisation(gsonBuilder);
		registerDateSerialisation(gsonBuilder);

		markSetAndMapFields(gsonBuilder);

		return gsonBuilder;
	}

	private static void registerTypesWithCustomMatchersSerialisation(GsonBuilder gsonBuilder, final Map<Class<?>, Matcher<?>> typesWithCustomMatchers) {
		gsonBuilder.registerTypeAdapterFactory(new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
				final TypeAdapter<T> delegateAdapter = gson.getDelegateAdapter(this, type);

				if (typesWithCustomMatchers.get(type.getRawType()) == null) {
					return null;
				}

				return new TypeAdapter<T>() {
					@Override
					public void write(JsonWriter out, T value) throws IOException {
						Matcher<?> matcher = typesWithCustomMatchers.get(type.getRawType());
						if (!matcher.matches(value)) {
							String jsonSnippet = null;
							JsonElement jsonElement = delegateAdapter.toJsonTree(value);

							if (!jsonElement.isJsonPrimitive() && !jsonElement.isJsonNull()) {
								jsonSnippet = gson.toJson(jsonElement);
							}
							throw new CustomMatcherException(value, matcher, type.getRawType().getSimpleName(), jsonSnippet);
						}

						out.nullValue();
					}

					@Override
					public T read(JsonReader in) throws IOException {
						return null;
					}
				};
			}
		});
	}

	private static void registerExclusionStrategies(GsonBuilder gsonBuilder, final List<Class<?>> typesToIgnore, final List<Matcher<String>> fieldsToIgnore) {
		if (typesToIgnore.isEmpty() && fieldsToIgnore.isEmpty()) {
			return;
		}
		
		gsonBuilder.setExclusionStrategies(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                for (Matcher<String> p : fieldsToIgnore) {
                    if (p.matches(f.getName())) {
                        return true;
                    }
                }
                return false;
            }
            
            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return (typesToIgnore.contains(clazz));
            }
        });
	}

	private static void markSetAndMapFields(final GsonBuilder gsonBuilder) {
		gsonBuilder.setFieldNamingStrategy(new FieldNamingStrategy() {
			@Override
			public String translateName(Field f) {
				if (Set.class.isAssignableFrom(f.getType()) || Map.class.isAssignableFrom(f.getType())) {
					return MARKER + f.getName();
				}
				return f.getName();
			}
		});
	}

	private static void registerMapSerialisation(final GsonBuilder gsonBuilder) {
		gsonBuilder.registerTypeHierarchyAdapter(Map.class, new JsonSerializer<Map<Object, Object>>() {
			@Override
			public JsonElement serialize(Map<Object, Object> map, Type type, JsonSerializationContext context) {
				Gson gson = gsonBuilder.create();
				
        		ArrayListMultimap<String, Object> objects = mapObjectsByTheirJsonRepresentation(map, gson);
        		return arrayOfObjectsOrderedByTheirJsonRepresentation(gson, objects, map);
			}
		});
	}

	private static void registerSetSerialisation(final GsonBuilder gsonBuilder) {
		gsonBuilder.registerTypeHierarchyAdapter(Set.class, new JsonSerializer<Set<Object>>() {
        	@Override
        	public JsonElement serialize(Set<Object> set, Type type, JsonSerializationContext context) {
        		Gson gson = gsonBuilder.create();

        		Set<Object> orderedSet = orderSetByElementsJsonRepresentation(set, gson);
        		return arrayOfObjectsOrderedByTheirJsonRepresentation(gson, orderedSet);
        	}
        });
	}

	private static void registerDateSerialisation(final GsonBuilder gsonBuilder) {
		gsonBuilder.registerTypeHierarchyAdapter(Date.class, new JsonSerializer<Date>() {
        	@Override
        	public JsonElement serialize(Date date, Type type, JsonSerializationContext context) {
        		return new JsonPrimitive(DATE_FORMAT.format(date));
        	}
        });
	}

	private static void registerCircularReferenceTypes(Set<Class<?>> circularReferenceTypes, GsonBuilder gsonBuilder) {
    	if (!circularReferenceTypes.isEmpty()) {
			GraphAdapterBuilder graphAdapterBuilder = new GraphAdapterBuilder();
			for (Class<?> circularReferenceType : circularReferenceTypes) {
				graphAdapterBuilder.addType(circularReferenceType);
			}
			graphAdapterBuilder.registerOn(gsonBuilder);
		}
	}

	private static void registerGuavaOptionalSerialisation(GsonBuilder gsonBuilder) {
		gsonBuilder.registerTypeAdapter(Optional.class, new JsonSerializer<Optional<Object>>() {
			@Override
			public JsonElement serialize(Optional<Object> src, Type typeOfSrc, JsonSerializationContext context) {
				JsonArray result = new JsonArray();
				result.add(context.serialize(src.orNull()));
				return result;
			}
		});
	}

	private static Set<Object> orderSetByElementsJsonRepresentation(Set<Object> set, final Gson gson) {
		Set<Object> objects = newTreeSet(new Comparator<Object>() {
			@Override
			public int compare(Object o1, Object o2) {
				return gson.toJson(o1).compareTo(gson.toJson(o2));
			}
		});
		objects.addAll(set);
		return objects;
	}

	private static ArrayListMultimap<String, Object> mapObjectsByTheirJsonRepresentation(Map<Object, Object> map, Gson gson) {
    	ArrayListMultimap<String, Object> objects = ArrayListMultimap.create();
    	for (Entry<Object, Object> mapEntry : map.entrySet()) {
    		objects.put(gson.toJson(mapEntry.getKey()).concat(gson.toJson(mapEntry.getValue())), mapEntry.getKey());
    	}
    	return objects;
    }

	private static JsonArray arrayOfObjectsOrderedByTheirJsonRepresentation(Gson gson, Set<Object> objects) {
		JsonArray array = new JsonArray();
		for (Object object : objects) {
			array.add(gson.toJsonTree(object));
		}
		return array;
	}

	private static JsonArray arrayOfObjectsOrderedByTheirJsonRepresentation(Gson gson, ArrayListMultimap<String, Object> objects, Map<Object, Object> map) {
		ImmutableList<String> sortedMapKeySet = Ordering.natural().immutableSortedCopy(objects.keySet());
		JsonArray array = new JsonArray();
		if (allKeysArePrimitiveOrStringOrEnum(sortedMapKeySet, objects)) {
			for (String jsonRepresentation : sortedMapKeySet) {
				List<Object> objectsInTheSet = objects.get(jsonRepresentation);
				for (Object objectInTheSet : objectsInTheSet) {
					JsonObject jsonObject = new JsonObject();
					jsonObject.add(String.valueOf(objectInTheSet), gson.toJsonTree(map.get(objectInTheSet)));
					array.add(jsonObject);
				}
			}
		} else {
			for (String jsonRepresentation : sortedMapKeySet) {
				JsonArray keyValueArray = new JsonArray();
				List<Object> objectsInTheSet = objects.get(jsonRepresentation);
				for (Object objectInTheSet : objectsInTheSet) {
					keyValueArray.add(gson.toJsonTree(objectInTheSet));
					keyValueArray.add(gson.toJsonTree(map.get(objectInTheSet)));
					array.add(keyValueArray);
				}
			}
		}

		return array;
	}

    private static boolean allKeysArePrimitiveOrStringOrEnum(ImmutableList<String> sortedMapKeySet, ArrayListMultimap<String, Object> objects) {
    	for (String jsonRepresentation : sortedMapKeySet) {
			List<Object> mapKeys = objects.get(jsonRepresentation);
			for (Object object : mapKeys) {
				if (!(isPrimitiveOrWrapper(object.getClass()) || object.getClass() == String.class || object.getClass().isEnum())) {
					return false;
				}
			}
		}
		return true;
	}

	private static GsonBuilder initGson() {
		return new GsonBuilder().setPrettyPrinting();
	}

	private static List<Class<?>> both(Collection<Class<?>> c1, Collection<Class<?>> c2) {
		List<Class<?>> list = new ArrayList<Class<?>>();

		list.addAll(c1);
		list.addAll(c2);

		return list;
	}
}
