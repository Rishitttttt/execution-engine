package com.ocee.worker;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.ocee.worker")
class ArchitectureTest {

    @ArchTest
    static final ArchRule worker_does_not_use_jpa =
            noClasses().should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..", "org.hibernate..");

    @ArchTest
    static final ArchRule worker_does_not_use_jdbc =
            noClasses().should().dependOnClassesThat()
                    .resideInAnyPackage("org.postgresql..", "org.springframework.jdbc..");
}
