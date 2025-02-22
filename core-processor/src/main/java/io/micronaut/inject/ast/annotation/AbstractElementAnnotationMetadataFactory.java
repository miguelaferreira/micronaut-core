/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.WildcardElement;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Abstract element annotation metadata factory.
 *
 * @param <K> The element type
 * @param <A> The annotation type
 * @author Denis Stepanov
 * @since 4.0.0
 */
public abstract class AbstractElementAnnotationMetadataFactory<K, A> implements ElementAnnotationMetadataFactory {

    protected final boolean isReadOnly;
    protected final AbstractAnnotationMetadataBuilder<K, A> metadataBuilder;

    protected AbstractElementAnnotationMetadataFactory(boolean isReadOnly, AbstractAnnotationMetadataBuilder<K, A> metadataBuilder) {
        this.isReadOnly = isReadOnly;
        this.metadataBuilder = metadataBuilder;
    }

    @Override
    public ElementAnnotationMetadata build(Element element) {
        return build(element, null);
    }

    @Override
    public ElementAnnotationMetadata build(Element element, AnnotationMetadata defaultAnnotationMetadata) {
        if (element instanceof ClassElement classElement) {
            return buildForClass(defaultAnnotationMetadata, classElement);
        }
        if (element instanceof ConstructorElement constructorElement) {
            return buildForConstructor(defaultAnnotationMetadata, constructorElement);
        }
        if (element instanceof MethodElement methodElement) {
            return buildForMethod(defaultAnnotationMetadata, methodElement);
        }
        if (element instanceof FieldElement fieldElement) {
            return buildForField(defaultAnnotationMetadata, fieldElement);
        }
        if (element instanceof ParameterElement parameterElement) {
            return buildForParameter(defaultAnnotationMetadata, parameterElement);
        }
        if (element instanceof PackageElement packageElement) {
            return buildForPackage(defaultAnnotationMetadata, packageElement);
        }
        if (element instanceof PropertyElement propertyElement) {
            return buildForProperty(defaultAnnotationMetadata, propertyElement);
        }
        if (element instanceof EnumConstantElement enumConstantElement) {
            return buildForEnumConstantElement(defaultAnnotationMetadata, enumConstantElement);
        }
        throw new IllegalStateException("Unknown element: " + element.getClass() + " with native type: " + element.getNativeType());
    }

    @Override
    public ElementAnnotationMetadata buildTypeAnnotations(ClassElement element) {
        return buildTypeAnnotationsForClass(element);
    }

    @Override
    public ElementAnnotationMetadata buildGenericTypeAnnotations(GenericElement element) {
        if (element instanceof GenericPlaceholderElement placeholderElement) {
            return buildTypeAnnotationsForGenericPlaceholder(null, placeholderElement);
        }
        if (element instanceof WildcardElement wildcardElement) {
            return buildTypeAnnotationsForWildcard(null, wildcardElement);
        }
        throw new IllegalStateException("Unknown generic element: " + element.getClass() + " with native type: " + element.getNativeType());
    }

    /**
     * Resolve native element.
     *
     * @param element The element
     * @return The native element
     */
    protected K getNativeElement(Element element) {
        return (K) element.getNativeType();
    }

    /**
     * Lookup annotation metadata for the package.
     *
     * @param packageElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForPackage(PackageElement packageElement) {
        return metadataBuilder.lookupOrBuildForType(getNativeElement(packageElement));
    }

    /**
     * Lookup annotation metadata for the parameter.
     *
     * @param parameterElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForParameter(ParameterElement parameterElement) {
        return metadataBuilder.lookupOrBuildForParameter(
            getNativeElement(parameterElement.getMethodElement().getOwningType()),
            getNativeElement(parameterElement.getMethodElement()),
            getNativeElement(parameterElement)
        );
    }

    /**
     * Lookup annotation metadata for the field.
     *
     * @param fieldElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForField(FieldElement fieldElement) {
        return metadataBuilder.lookupOrBuildForField(
            getNativeElement(fieldElement.getOwningType()),
            getNativeElement(fieldElement)
        );
    }

    /**
     * Lookup annotation metadata for the method.
     *
     * @param methodElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForMethod(MethodElement methodElement) {
        return metadataBuilder.lookupOrBuildForMethod(
            getNativeElement(methodElement.getOwningType()),
            getNativeElement(methodElement)
        );
    }

    /**
     * Lookup annotation metadata for the class.
     *
     * @param classElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForClass(ClassElement classElement) {
        return metadataBuilder.lookupOrBuildForType(getNativeElement(classElement));
    }

    /**
     * Lookup annotation metadata for the class.
     *
     * @param classElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForClass(ClassElement classElement) {
        return new AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return AnnotationMetadata.EMPTY_METADATA;
            }

            @Override
            public boolean isMutated() {
                return false;
            }

            @Override
            public void update(AnnotationMetadata annotationMetadata) {
                throw new IllegalStateException("Class element: [" + classElement + "] doesn't support type annotations!");
            }
        };
    }

    /**
     * Lookup annotation metadata for the placeholder.
     *
     * @param placeholderElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForGenericPlaceholder(GenericPlaceholderElement placeholderElement) {
        return metadataBuilder.lookupOrBuildForType(getNativeElement(placeholderElement));
    }

    /**
     * Lookup annotation metadata for the wildcard.
     *
     * @param wildcardElement The element
     * @return The annotation metadata
     */
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForWildcard(WildcardElement wildcardElement) {
        return metadataBuilder.lookupOrBuildForType(getNativeElement(wildcardElement));
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForProperty(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull PropertyElement propertyElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                throw new IllegalStateException("Properties should combine annotations for it's elements!");
            }

            @Override
            public String toString() {
                return propertyElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForEnumConstantElement(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                                          @NonNull EnumConstantElement enumConstantElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForField(enumConstantElement);
            }

            @Override
            public String toString() {
                return enumConstantElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForPackage(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull PackageElement packageElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForPackage(packageElement);
            }

            @Override
            public String toString() {
                return packageElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForParameter(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull ParameterElement parameterElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForParameter(parameterElement);
            }

            @Override
            public String toString() {
                return parameterElement.toString();
            }

        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForField(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull FieldElement fieldElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForField(fieldElement);
            }

            @Override
            public String toString() {
                return fieldElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForMethod(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                             @NonNull MethodElement methodElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AnnotationMetadata[] getParentAnnotationMetadata() {
                return new AnnotationMetadata[]{methodElement.getOwningType()};
            }

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForMethod(methodElement);
            }

            @Override
            public String toString() {
                return methodElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForConstructor(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                                  @NonNull ConstructorElement constructorElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForMethod(constructorElement);
            }

            @Override
            public String toString() {
                return constructorElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildTypeAnnotationsForClass(@NonNull ClassElement classElement) {
        return new AbstractElementAnnotationMetadata(null) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupTypeAnnotationsForClass(classElement);
            }

            @Override
            public String toString() {
                return classElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildForClass(@Nullable AnnotationMetadata defaultAnnotationMetadata, @NonNull ClassElement classElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupForClass(classElement);
            }

            @Override
            public String toString() {
                return classElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildTypeAnnotationsForGenericPlaceholder(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                                                        @NonNull GenericPlaceholderElement placeholderElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupTypeAnnotationsForGenericPlaceholder(placeholderElement);
            }

            @Override
            public String toString() {
                return placeholderElement.toString();
            }
        };
    }

    @NonNull
    private AbstractElementAnnotationMetadata buildTypeAnnotationsForWildcard(@Nullable AnnotationMetadata defaultAnnotationMetadata,
                                                                              @NonNull WildcardElement wildcardElement) {
        return new AbstractElementAnnotationMetadata(defaultAnnotationMetadata) {

            @Override
            protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup() {
                return lookupTypeAnnotationsForWildcard(wildcardElement);
            }

            @Override
            public String toString() {
                return wildcardElement.toString();
            }
        };
    }

    /**
     * Abstract implementation of {@link ElementAnnotationMetadata}.
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    protected abstract class AbstractElementAnnotationMetadata implements ElementAnnotationMetadata {

        protected AnnotationMetadata preloadedAnnotationMetadata;
        private final boolean readOnly;
        private AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata cacheEntry;
        private AnnotationMetadata cacheAnnotationMetadata;

        protected AbstractElementAnnotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
            this(AbstractElementAnnotationMetadataFactory.this.isReadOnly, annotationMetadata);
        }

        protected AbstractElementAnnotationMetadata(boolean readOnly,
                                                    @Nullable AnnotationMetadata annotationMetadata) {
            this.readOnly = readOnly;
            this.preloadedAnnotationMetadata = annotationMetadata;
            if (preloadedAnnotationMetadata instanceof AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata) {
                throw new IllegalStateException();
            }
            if (preloadedAnnotationMetadata instanceof ElementAnnotationMetadata) {
                throw new IllegalStateException();
            }
        }

        /**
         * @return The parent annotation metadata for the annotation hierarchy
         */
        @NonNull
        protected AnnotationMetadata[] getParentAnnotationMetadata() {
            return new AnnotationMetadata[0];
        }

        protected abstract AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookup();

        private AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata getCacheEntry() {
            if (cacheEntry == null) {
                cacheEntry = lookup();
            }
            return cacheEntry;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            if (cacheAnnotationMetadata == null) {
                if (preloadedAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
                    return preloadedAnnotationMetadata;
                }
                AnnotationMetadata[] parentAnnotationMetadata = getParentAnnotationMetadata();
                AnnotationMetadata annotationMetadata;
                if (preloadedAnnotationMetadata != null) {
                    annotationMetadata = preloadedAnnotationMetadata;
                } else {
                    annotationMetadata = getCacheEntry();
                }
                if (parentAnnotationMetadata.length > 0) {
                    cacheAnnotationMetadata = new AnnotationMetadataHierarchy(
                        Stream.concat(Arrays.stream(parentAnnotationMetadata), Stream.of(annotationMetadata)).toArray(AnnotationMetadata[]::new)
                    );
                } else {
                    cacheAnnotationMetadata = annotationMetadata;
                }
            }
            return cacheAnnotationMetadata;
        }

        private AnnotationMetadata getAnnotationMetadataToModify() {
            if (preloadedAnnotationMetadata instanceof AnnotationMetadataHierarchy) {
                return preloadedAnnotationMetadata.getDeclaredMetadata().copyAnnotationMetadata();
            }
            if (preloadedAnnotationMetadata != null) {
                return preloadedAnnotationMetadata;
            }
            return getCacheEntry().copyAnnotationMetadata();
        }

        private AnnotationMetadata replaceAnnotationsInternal(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata instanceof AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata) {
                throw new IllegalStateException();
            }
            if (annotationMetadata instanceof MutableAnnotationMetadataDelegate) {
                throw new IllegalStateException();
            }
            if (annotationMetadata.isEmpty()) {
                annotationMetadata = EMPTY_METADATA;
            }
            if (!readOnly) {
                if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
                    throw new IllegalStateException("Not supported to cache AnnotationMetadataHierarchy");
                }
                getCacheEntry().update(annotationMetadata);
                preloadedAnnotationMetadata = null;
            } else {
                preloadedAnnotationMetadata = annotationMetadata;
            }
            cacheAnnotationMetadata = null;
            return getAnnotationMetadata();
        }

        @Override
        @SuppressWarnings("java:S1192")
        public <T extends Annotation> AnnotationMetadata annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType, metadataBuilder.getRetentionPolicy(annotationType));
            //noinspection ConstantConditions
            if (consumer != null) {
                consumer.accept(builder);
                AnnotationValue<T> av = builder.build();
                return replaceAnnotationsInternal(metadataBuilder.annotate(getAnnotationMetadataToModify(), av));
            }
            return getAnnotationMetadata();
        }

        @Override
        public <T extends Annotation> AnnotationMetadata annotate(AnnotationValue<T> annotationValue) {
            ArgumentUtils.requireNonNull("annotationValue", annotationValue);
            return replaceAnnotationsInternal(metadataBuilder.annotate(getAnnotationMetadataToModify(), annotationValue));
        }

        @Override
        public AnnotationMetadata removeAnnotation(@NonNull String annotationType) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            return replaceAnnotationsInternal(metadataBuilder.removeAnnotation(getAnnotationMetadataToModify(), annotationType));
        }

        @Override
        public <T extends Annotation> AnnotationMetadata removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
            ArgumentUtils.requireNonNull("predicate", predicate);
            return replaceAnnotationsInternal(metadataBuilder.removeAnnotationIf(getAnnotationMetadataToModify(), predicate));
        }

        @Override
        public AnnotationMetadata removeStereotype(@NonNull String annotationType) {
            ArgumentUtils.requireNonNull("annotationType", annotationType);
            return replaceAnnotationsInternal(metadataBuilder.removeStereotype(getAnnotationMetadataToModify(), annotationType));
        }

    }

}
