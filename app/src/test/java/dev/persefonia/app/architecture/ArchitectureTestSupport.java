package dev.persefonia.app.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

final class ArchitectureTestSupport {
    static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.persefonia");

    static final String[] BOUNDED_CONTEXT_PACKAGES = {
            "dev.persefonia.identityaccess..",
            "dev.persefonia.taxonomy..",
            "dev.persefonia.contentpublishing..",
            "dev.persefonia.profileportfolio..",
            "dev.persefonia.medialibrary..",
            "dev.persefonia.communication..",
            "dev.persefonia.discovery..",
            "dev.persefonia.contentintegrity..",
            "dev.persefonia.insights..",
            "dev.persefonia.audit..",
            "dev.persefonia.portability..",
            "dev.persefonia.platformoperations.."
    };

    private ArchitectureTestSupport() {
    }
}
