/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.generics

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Unroll

import javax.validation.ConstraintViolationException
import java.util.function.Function
import java.util.function.Supplier

class GenericTypeArgumentsSpec extends AbstractBeanDefinitionSpec {

    void "test type arguments for exception handler"() {
        given:
        BeanDefinition definition = buildBeanDefinition('exceptionhandler.Test', '''\
package exceptionhandler;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;
import javax.validation.ConstraintViolationException;

@Context
class Test implements ExceptionHandler<ConstraintViolationException, java.util.function.Supplier<Foo>> {

    public java.util.function.Supplier<Foo> handle(String request, ConstraintViolationException e) {
        return null;
    }
}

interface Foo {}
interface ExceptionHandler<T extends Throwable, R> {
    R handle(String request, T exception);
}
''')
        expect:
        definition != null
        def typeArgs = definition.getTypeArguments("exceptionhandler.ExceptionHandler")
        typeArgs.size() == 2
        typeArgs[0].type == ConstraintViolationException
        typeArgs[1].type == Supplier
    }

    void "test type arguments for factory returning interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('factorygenerics.Test$MyFunc0', '''\
package factorygenerics;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    io.micronaut.context.event.BeanCreatedEventListener<Foo> myFunc() {
        return (event) -> event.getBean();
    }
}

interface Foo {}

''')
        expect:
        definition != null
        definition.getTypeArguments(BeanCreatedEventListener).size() == 1
        definition.getTypeArguments(BeanCreatedEventListener)[0].type.name == 'factorygenerics.Foo'
    }

    @Unroll
    void "test generic return type resolution for return type: #returnType"() {
        given:
        BeanDefinition definition = buildBeanDefinition('generreturntest1.Test', """\
package generreturntest1;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class Test {

    @Executable
    public $returnType test() {
        return null;
    }
}
""")
        def method = definition.getRequiredMethod("test")

        expect:
        method.getDescription(true).startsWith("$returnType" )

        where:
        returnType <<
        ['List<Map<String, Integer>>',
        'List<List<String>>',
        'List<String>',
        'Map<String, Integer>']
    }

    void "test type arguments for interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('generictest1.GenericsTest1', '''\
package generictest1;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class GenericsTest1 implements java.util.function.Function<String, Integer>{

    public Integer apply(String str) {
        return 10;
    }
}

class Foo {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for inherited interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('generictest2.GenericsTest2', '''\
package generictest2;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class GenericsTest2 implements Foo {

    public Integer apply(String str) {
        return 10;
    }
}

interface Foo extends java.util.function.Function<String, Integer> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }


    void "test type arguments for superclass with interface"() {
        given:
        BeanDefinition definition = buildBeanDefinition('generictest3.GenericsTest3', '''\
package generictest3;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class GenericsTest3 extends Foo {

    public Integer apply(String str) {
        return 10;
    }
}

abstract class Foo implements java.util.function.Function<String, Integer> {}
''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for superclass"() {
        given:
        BeanDefinition definition = buildBeanDefinition('generictest4.GenericsTest4', '''\
package generictest4;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class GenericsTest4 extends Foo<String, Integer> {

    public Integer apply(String str) {
        return 10;
    }
}

abstract class Foo<T, R> {

    abstract R apply(T t);
}
''')
        expect:
        definition != null
        definition.getTypeArguments('generictest4.Foo').size() == 2
        definition.getTypeArguments('generictest4.Foo')[0].name == 'T'
        definition.getTypeArguments('generictest4.Foo')[1].name == 'R'
        definition.getTypeArguments('generictest4.Foo')[0].type == String
        definition.getTypeArguments('generictest4.Foo')[1].type == Integer
    }

    void "test type arguments for factory"() {
        given:
        BeanDefinition definition = buildBeanDefinition('generictest5.TestFactory$MyFunc0', '''\
package generictest5;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean
    java.util.function.Function<String, Integer> myFunc() {
        return { String str -> 10 };
    }
}

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for factory with AOP advice applied"() {
        given:
        BeanDefinition definition = buildBeanDefinition('generictest6.$TestFactory$MyFunc0' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''\
package generictest6;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class TestFactory {

    @Bean
    @io.micronaut.aop.interceptors.Mutating
    java.util.function.Function<String, Integer> myFunc() {
        return { String str -> 10 };
    }
}

''')
        expect:
        definition != null
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }

    void "test type arguments for methods"() {
        BeanDefinition definition = buildBeanDefinition('generictest7.StatusController', '''
package generictest7;

import io.micronaut.http.annotation.*;

class GenericController<T> {

    @Post
    T save(@Body T entity) {
        return entity;
    }
}

@Controller
class StatusController extends GenericController<String> {

}
''')
        List<ExecutableMethod> methods = definition.getExecutableMethods().toList()

        expect:
        definition != null
        methods.size() == 1
        methods[0].getArguments()[0].type == String
        methods[0].getReturnType().type == String
    }

    void "test replacing an impl with an interface"() {
        BeanDefinition definition = buildBeanDefinition('generictest8.FactoryReplace$TestService0', '''
package generictest8

import io.micronaut.context.annotation.*
import io.micronaut.inject.generics.missing.*
import io.micronaut.aop.interceptors.Mutating

@Factory
class FactoryReplace {

    @Replaces(TestServiceImpl)
    @Mutating("name")
    @Bean
    TestService testService() {
        new TestServiceImpl()
    }
}
''')

        expect:
        definition != null
    }

    void "test recusive generic type parameter"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test.TrackedSortedSet', '''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
final class TrackedSortedSet<T extends java.lang.Comparable<? super T>> {
 public TrackedSortedSet(java.util.Collection<? extends T> initial) {
        super();
    }
}

''')
        expect:
            definition != null
    }
}
