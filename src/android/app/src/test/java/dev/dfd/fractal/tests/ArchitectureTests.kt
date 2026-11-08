package dev.dfd.fractal.tests

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.alwaysTrue
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.base.DescribedPredicate.or
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.junit.ArchUnitRunner
import com.tngtech.archunit.junit.CacheMode
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import org.junit.runner.RunWith

@RunWith(ArchUnitRunner::class)
@AnalyzeClasses(
    packages = ["dev.dfd.fractal"],
    importOptions = [DoNotIncludeTests::class],
    cacheMode = CacheMode.PER_CLASS
)
class ArchitectureTests {
    private val isFromEntryPointCode =
        object : DescribedPredicate<JavaClass>("is from entry point code") {
            private val entryPointFiles = listOf(
                "MainActivity.kt",
                "CemuApplication.kt",
            )

            override fun test(input: JavaClass?): Boolean {
                return entryPointFiles.any { input?.sourceCodeLocation?.sourceFileName == it }
            }
        }

    private val isFromGeneratedCode =
        object : DescribedPredicate<JavaClass>("is from generated code") {
            override fun test(input: JavaClass?): Boolean {
                if (isFromEntryPointCode.test(input)) {
                    return false
                }

                return input?.packageName == "dev.dfd.fractal"
                        || input?.packageName?.contains("databinding") ?: false
            }
        }

    private val isFromTests = resideInAPackage("dev.dfd.fractal.tests..")

    private val isFromNativeInterface = resideInAPackage("dev.dfd.fractal.nativeinterface..")

    private fun JavaClasses.thatAreFromMainSources() =
        that(not(or(isFromGeneratedCode, isFromTests)))

    @ArchTest
    fun `feature packages should not depend on each other`(javaClasses: JavaClasses) {
        SlicesRuleDefinition.slices().matching("dev.dfd.fractal.(*)..")
            .should().notDependOnEachOther()
            .ignoreDependency(
                alwaysTrue(),
                or(
                    resideInAPackage("dev.dfd.fractal.common.."),
                    resideInAPackage("dev.dfd.fractal.nativeinterface.."),
                    resideInAPackage("dev.dfd.fractal.databinding..")
                )
            )
            .check(javaClasses.thatAreFromMainSources())
    }

    @ArchTest
    fun `no packages should depend on entry point code`(javaClasses: JavaClasses) {
        noClasses().that(not(isFromEntryPointCode))
            .should().dependOnClassesThat(isFromEntryPointCode)
            .check(javaClasses.thatAreFromMainSources())
    }

    @ArchTest
    fun `native interface should not depend on any other app packages`(javaClasses: JavaClasses) {
        noClasses().that(isFromNativeInterface)
            .should().dependOnClassesThat(
                resideInAPackage("dev.dfd.fractal..")
                    .and(not(isFromNativeInterface))
            )
            .check(javaClasses.thatAreFromMainSources())
    }
}
