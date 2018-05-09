/*
 * Copyright 2018 original authors
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
package io.micronaut.aop.compile

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
class IntroductionAnnotationSpec extends AbstractTypeElementSpec{


    void "test @Min annotation"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.introduction.*;
import io.micronaut.context.annotation.*;
import java.net.*;
import javax.validation.constraints.*;

interface MyInterface{
    void save(@NotBlank String name, @Min(1L) int age);
    void saveTwo(@Min(1L) String name);
}


@Stub
@javax.inject.Singleton
interface MyBean extends MyInterface {
}

''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        beanDefinition.executableMethods[0].methodName == 'save'
        beanDefinition.executableMethods[0].returnType.type == void.class
        beanDefinition.executableMethods[0].arguments[0].getAnnotationMetadata().hasAnnotation(NotBlank)
        beanDefinition.executableMethods[0].arguments[1].getAnnotationMetadata().hasAnnotation(Min)
        beanDefinition.executableMethods[0].arguments[1].getAnnotationMetadata().getValue(Min, Integer).get() == 1

        beanDefinition.executableMethods[1].methodName == 'saveTwo'
        beanDefinition.executableMethods[1].returnType.type == void.class
        beanDefinition.executableMethods[1].arguments[0].getAnnotationMetadata().hasAnnotation(Min)

    }

}
