/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.validation.validator.ExecutableMethodValidator;
import io.micronaut.validation.validator.ReactiveValidator;
import io.micronaut.validation.validator.Validator;
import jakarta.inject.Singleton;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidatorFactory;
import javax.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * A {@link MethodInterceptor} that validates method invocations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class ValidatingInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * The position of the interceptor. See {@link io.micronaut.core.order.Ordered}
     */
    public static final int POSITION = InterceptPhase.VALIDATE.getPosition();

    private final ConversionService conversionService;
    private final @Nullable ExecutableValidator executableValidator;
    private final @Nullable ExecutableMethodValidator micronautValidator;

    /**
     * Creates ValidatingInterceptor from the validatorFactory.
     *
     * @param micronautValidator The micronaut validator use if no factory is available
     * @param validatorFactory   Factory returning initialized {@code Validator} instances
     * @param conversionService  The conversion service
     */
    public ValidatingInterceptor(@Nullable Validator micronautValidator,
                                 @Nullable ValidatorFactory validatorFactory,
                                 ConversionService conversionService) {
        this.conversionService = conversionService;

        if (validatorFactory != null) {
            javax.validation.Validator validator = validatorFactory.getValidator();
            if (validator instanceof Validator) {
                this.micronautValidator = (ExecutableMethodValidator) validator;
                this.executableValidator = null;
            } else {
                this.micronautValidator = null;
                this.executableValidator = validator.forExecutables();
            }
        } else if (micronautValidator != null) {
            this.micronautValidator = micronautValidator.forExecutables();
            this.executableValidator = null;
        } else {
            this.micronautValidator = null;
            this.executableValidator = null;
        }
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (executableValidator != null) {
            Method targetMethod = context.getTargetMethod();
            if (targetMethod.getParameterTypes().length != 0) {
                Set<ConstraintViolation<Object>> constraintViolations = executableValidator
                        .validateParameters(
                                context.getTarget(),
                                targetMethod,
                                context.getParameterValues(),
                                getValidationGroups(context)
                        );
                if (!constraintViolations.isEmpty()) {
                    throw new ConstraintViolationException(constraintViolations);
                }
            }
            return validateReturnExecutableValidator(context, targetMethod);
        } else if (micronautValidator != null) {
            ExecutableMethod<Object, Object> executableMethod = context.getExecutableMethod();
            if (executableMethod.getArguments().length != 0) {
                Set<ConstraintViolation<Object>> constraintViolations = micronautValidator.validateParameters(
                        context.getTarget(),
                        executableMethod,
                        context.getParameterValues(),
                        getValidationGroups(context));
                if (!constraintViolations.isEmpty()) {
                    throw new ConstraintViolationException(constraintViolations);
                }
            }
            if (hasValidationAnnotation(context)) {
                if (micronautValidator instanceof ReactiveValidator) {
                    InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
                    try {
                        return switch (interceptedMethod.resultType()) {
                            case PUBLISHER -> interceptedMethod.handleResult(
                                    ((ReactiveValidator) micronautValidator).validatePublisher(
                                            interceptedMethod.interceptResultAsPublisher(),
                                            getValidationGroups(context))
                            );
                            case COMPLETION_STAGE -> interceptedMethod.handleResult(
                                    ((ReactiveValidator) micronautValidator).validateCompletionStage(
                                            interceptedMethod.interceptResultAsCompletionStage(),
                                            getValidationGroups(context))
                            );
                            case SYNCHRONOUS -> validateReturnMicronautValidator(context, executableMethod);
                            default -> interceptedMethod.unsupported();
                        };
                    } catch (Exception e) {
                        return interceptedMethod.handleException(e);
                    }
                } else {
                    return validateReturnMicronautValidator(context, executableMethod);
                }
            }
            return context.proceed();
        }
        return context.proceed();
    }

    private Object validateReturnMicronautValidator(MethodInvocationContext<Object, Object> context, ExecutableMethod<Object, Object> executableMethod) {
        Object result = context.proceed();
        Set<ConstraintViolation<Object>> constraintViolations = micronautValidator.validateReturnValue(
                context.getTarget(),
                executableMethod,
                result,
                getValidationGroups(context));
        if (!constraintViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations);
        }
        return result;
    }

    private Object validateReturnExecutableValidator(MethodInvocationContext<Object, Object> context, Method targetMethod) {
        final Object result = context.proceed();
        if (hasValidationAnnotation(context)) {
            Set<ConstraintViolation<Object>> constraintViolations = executableValidator.validateReturnValue(
                    context.getTarget(),
                    targetMethod,
                    result,
                    getValidationGroups(context)
            );
            if (!constraintViolations.isEmpty()) {
                throw new ConstraintViolationException(constraintViolations);
            }
        }
        return result;
    }

    private boolean hasValidationAnnotation(MethodInvocationContext<Object, Object> context) {
        return context.hasStereotype(Validator.ANN_VALID) || context.hasStereotype(Validator.ANN_CONSTRAINT);
    }

    private Class<?>[] getValidationGroups(MethodInvocationContext<Object, Object> context) {
      return context.classValues(Validated.class, "groups");
    }
}
