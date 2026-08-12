package com.ikunkk02.wishingwillow.program;

/** Carries structured local-normalization failure details into the optional AI repair prompt. */
public final class WishProgramNormalizationException extends IllegalArgumentException {
    private final WishProgramValidationIssue issue;

    public WishProgramNormalizationException(WishProgramValidationIssue issue) {
        super(issue.validationError());
        this.issue = issue;
    }

    public WishProgramValidationIssue issue() {
        return issue;
    }
}
