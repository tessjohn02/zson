/*
 * Copyright (C) 2020 ZeoFlow
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

package com.zeoflow.zson.functional;

import com.zeoflow.zson.Zson;
import com.zeoflow.zson.ZsonBuilder;
import com.zeoflow.zson.JsonDeserializationContext;
import com.zeoflow.zson.JsonDeserializer;
import com.zeoflow.zson.JsonElement;
import com.zeoflow.zson.JsonParseException;
import com.zeoflow.zson.JsonPrimitive;
import com.zeoflow.zson.JsonSerializationContext;
import com.zeoflow.zson.JsonSerializer;
import com.zeoflow.zson.TypeAdapter;
import com.zeoflow.zson.TypeAdapterFactory;
import com.zeoflow.zson.annotations.JsonAdapter;
import com.zeoflow.zson.reflect.TypeToken;
import com.zeoflow.zson.stream.JsonReader;
import com.zeoflow.zson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Locale;
import junit.framework.TestCase;

/**
 * Functional tests for the {@link JsonAdapter} annotation on classes.
 */
public final class JsonAdapterAnnotationOnClassesTest extends TestCase {

  public void testJsonAdapterInvoked() {
    Zson zson = new Zson();
    String json = zson.toJson(new A("bar"));
    assertEquals("\"jsonAdapter\"", json);

   // Also invoke the JsonAdapter javadoc sample
    json = zson.toJson(new User("Inderjeet", "Singh"));
    assertEquals("{\"name\":\"Inderjeet Singh\"}", json);
    User user = zson.fromJson("{'name':'Joel Leitch'}", User.class);
    assertEquals("Joel", user.firstName);
    assertEquals("Leitch", user.lastName);

    json = zson.toJson(Foo.BAR);
    assertEquals("\"bar\"", json);
    Foo baz = zson.fromJson("\"baz\"", Foo.class);
    assertEquals(Foo.BAZ, baz);
  }

  public void testJsonAdapterFactoryInvoked() {
    Zson zson = new Zson();
    String json = zson.toJson(new C("bar"));
    assertEquals("\"jsonAdapterFactory\"", json);
    C c = zson.fromJson("\"bar\"", C.class);
    assertEquals("jsonAdapterFactory", c.value);
  }

  public void testRegisteredAdapterOverridesJsonAdapter() {
    TypeAdapter<A> typeAdapter = new TypeAdapter<A>() {
      @Override public void write(JsonWriter out, A value) throws IOException {
        out.value("registeredAdapter");
      }
      @Override public A read(JsonReader in) throws IOException {
        return new A(in.nextString());
      }
    };
    Zson zson = new ZsonBuilder()
      .registerTypeAdapter(A.class, typeAdapter)
      .create();
    String json = zson.toJson(new A("abcd"));
    assertEquals("\"registeredAdapter\"", json);
  }

  /**
   * The serializer overrides field adapter, but for deserializer the fieldAdapter is used.
   */
  public void testRegisteredSerializerOverridesJsonAdapter() {
    JsonSerializer<A> serializer = new JsonSerializer<A>() {
      public JsonElement serialize(A src, Type typeOfSrc,
          JsonSerializationContext context) {
        return new JsonPrimitive("registeredSerializer");
      }
    };
    Zson zson = new ZsonBuilder()
      .registerTypeAdapter(A.class, serializer)
      .create();
    String json = zson.toJson(new A("abcd"));
    assertEquals("\"registeredSerializer\"", json);
    A target = zson.fromJson("abcd", A.class);
    assertEquals("jsonAdapter", target.value);
  }

  /**
   * The deserializer overrides Json adapter, but for serializer the jsonAdapter is used.
   */
  public void testRegisteredDeserializerOverridesJsonAdapter() {
    JsonDeserializer<A> deserializer = new JsonDeserializer<A>() {
      public A deserialize(JsonElement json, Type typeOfT,
          JsonDeserializationContext context) throws JsonParseException
      {
        return new A("registeredDeserializer");
      }
    };
    Zson zson = new ZsonBuilder()
      .registerTypeAdapter(A.class, deserializer)
      .create();
    String json = zson.toJson(new A("abcd"));
    assertEquals("\"jsonAdapter\"", json);
    A target = zson.fromJson("abcd", A.class);
    assertEquals("registeredDeserializer", target.value);
  }

  public void testIncorrectTypeAdapterFails() {
    try {
      String json = new Zson().toJson(new ClassWithIncorrectJsonAdapter("bar"));
      fail(json);
    } catch (ClassCastException expected) {}
  }

  public void testSuperclassTypeAdapterNotInvoked() {
    String json = new Zson().toJson(new B("bar"));
    assertFalse(json.contains("jsonAdapter"));
  }

  public void testNullSafeObjectFromJson() {
    Zson zson = new Zson();
    NullableClass fromJson = zson.fromJson("null", NullableClass.class);
    assertNull(fromJson);
  }

  @JsonAdapter(A.JsonAdapter.class)
  private static class A {
    final String value;
    A(String value) {
      this.value = value;
    }
    static final class JsonAdapter extends TypeAdapter<A> {
      @Override public void write(JsonWriter out, A value) throws IOException {
        out.value("jsonAdapter");
      }
      @Override public A read(JsonReader in) throws IOException {
        in.nextString();
        return new A("jsonAdapter");
      }
    }
  }

  @JsonAdapter(C.JsonAdapterFactory.class)
  private static class C {
    final String value;
    C(String value) {
      this.value = value;
    }
    static final class JsonAdapterFactory implements TypeAdapterFactory {
      @Override public <T> TypeAdapter<T> create(Zson zson, final TypeToken<T> type) {
        return new TypeAdapter<T>() {
          @Override public void write(JsonWriter out, T value) throws IOException {
            out.value("jsonAdapterFactory");
          }
          @SuppressWarnings("unchecked")
          @Override public T read(JsonReader in) throws IOException {
            in.nextString();
            return (T) new C("jsonAdapterFactory");
          }
        };
      }
    }
  }

  private static final class B extends A {
    B(String value) {
      super(value);
    }
  }
  // Note that the type is NOT TypeAdapter<ClassWithIncorrectJsonAdapter> so this
  // should cause error
  @JsonAdapter(A.JsonAdapter.class)
  private static final class ClassWithIncorrectJsonAdapter {
    @SuppressWarnings("unused") final String value;
    ClassWithIncorrectJsonAdapter(String value) {
      this.value = value;
    }
  }

  // This class is used in JsonAdapter Javadoc as an example
  @JsonAdapter(UserJsonAdapter.class)
  private static class User {
    final String firstName, lastName;
    User(String firstName, String lastName) {
      this.firstName = firstName;
      this.lastName = lastName;
    }
  }
  private static class UserJsonAdapter extends TypeAdapter<User> {
    @Override public void write(JsonWriter out, User user) throws IOException {
      // implement write: combine firstName and lastName into name
      out.beginObject();
      out.name("name");
      out.value(user.firstName + " " + user.lastName);
      out.endObject();
      // implement the write method
    }
    @Override public User read(JsonReader in) throws IOException {
      // implement read: split name into firstName and lastName
      in.beginObject();
      in.nextName();
      String[] nameParts = in.nextString().split(" ");
      in.endObject();
      return new User(nameParts[0], nameParts[1]);
    }
  }

  @JsonAdapter(value = NullableClassJsonAdapter.class)
  private static class NullableClass {
  }

  private static class NullableClassJsonAdapter extends TypeAdapter<NullableClass> {
    @Override
    public void write(JsonWriter out, NullableClass value) throws IOException {
      out.value("nullable");
    }

    @Override
    public NullableClass read(JsonReader in) throws IOException {
      in.nextString();
      return new NullableClass();
    }
  }

  @JsonAdapter(FooJsonAdapter.class)
  private static enum Foo { BAR, BAZ }
  private static class FooJsonAdapter extends TypeAdapter<Foo> {
    @Override public void write(JsonWriter out, Foo value) throws IOException {
      out.value(value.name().toLowerCase(Locale.US));
    }

    @Override public Foo read(JsonReader in) throws IOException {
      return Foo.valueOf(in.nextString().toUpperCase(Locale.US));
    }
  }

  public void testIncorrectJsonAdapterType() {
    try {
      new Zson().toJson(new D());
      fail();
    } catch (IllegalArgumentException expected) {}
  }
  @JsonAdapter(Integer.class)
  private static final class D {
    @SuppressWarnings("unused") final String value = "a";
  }
}
