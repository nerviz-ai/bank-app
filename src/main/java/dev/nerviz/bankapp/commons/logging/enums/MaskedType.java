package dev.nerviz.bankapp.commons.logging.enums;

import lombok.Getter;

/**
 * Predefined masking regexes for {@code @MaskSensitiveData}. {@code @Getter} on {@code regex}
 * is allowed under {@code .claude/rules/lombok.md}: {@code String} is immutable.
 *
 * <p>Lives in the {@code enums} sub-package of {@code commons.logging}, its own small territory
 * since {@code MaskSensitiveData} ({@code annotations}) is its only consumer.
 */
@Getter
public enum MaskedType {
    ALL("\\S"),
    EMAIL(".(?=.{4})(?=[^@])(?=[^@]{4})."),
    DOCUMENT(".(?=.{3})"),
    NAME(".(?=[^ ])(?=[^ ]{2})."),
    DATE(".(?=[^ \\\\/.-].{3})."),
    ADDRESS(".(?=.{3})[^, ]"),
    ZIP_CODE(".(?=.{3})[^-]"),
    NUMBER("\\d"),
    TELEPHONE(".(?=.{2})[^-]");

    private final String regex;

    MaskedType(String regex) {
        this.regex = regex;
    }
}
