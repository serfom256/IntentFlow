package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class IntentServiceScanner extends ClassPathScanningCandidateComponentProvider {

    private final ClassLoader classLoader;

    IntentServiceScanner(ClassLoader classLoader) {
        super(false);
        this.classLoader = classLoader;
        addIncludeFilter(new AnnotationTypeFilter(IntentService.class));
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        return metadata.isIndependent() && !metadata.isAnnotation();
    }

    List<Class<?>> scan(Collection<String> packages) {
        Set<String> classNames = new LinkedHashSet<>();
        for (String pkg : packages) {
            for (BeanDefinition candidate : findCandidateComponents(pkg)) {
                classNames.add(candidate.getBeanClassName());
            }
        }
        return classNames.stream()
                .sorted(Comparator.naturalOrder())
                .<Class<?>>map(name -> ClassUtils.resolveClassName(name, classLoader))
                .toList();
    }
}
