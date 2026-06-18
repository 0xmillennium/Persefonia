package dev.persefonia.webadmin.cv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AdminCvFormValidator {
    public List<AdminCvFieldError> validate(AdminCvForm form) {
        List<AdminCvFieldError> errors = new ArrayList<>();
        validateAssetId("trAssetId", form.getTrAssetId(), errors);
        validateAssetId("enAssetId", form.getEnAssetId(), errors);
        validateDisplayLabel("trDisplayLabel", form.getTrDisplayLabel(), errors);
        validateDisplayLabel("enDisplayLabel", form.getEnDisplayLabel(), errors);
        return errors;
    }

    private static void validateAssetId(String field, String value, List<AdminCvFieldError> errors) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            errors.add(new AdminCvFieldError(field, "Must be a valid UUID."));
        }
    }

    private static void validateDisplayLabel(String field, String value, List<AdminCvFieldError> errors) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (value.length() > 160) {
            errors.add(new AdminCvFieldError(field, "Must be 160 characters or fewer."));
        }
    }
}
