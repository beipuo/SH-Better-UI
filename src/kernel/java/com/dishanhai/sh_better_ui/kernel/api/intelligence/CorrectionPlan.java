// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import java.util.List;
import java.util.Objects;

/** Detached proposal. Applying and rolling back both revalidate the current declaration and resources. */
public record CorrectionPlan(UISnapshot source, UISnapshot proposed, List<ValidationReport.Correction> operations) {
    public CorrectionPlan {
        Objects.requireNonNull(source); Objects.requireNonNull(proposed); operations = List.copyOf(operations);
    }
    public record Result(boolean applied, String reason, UISnapshot snapshot) {}
}
