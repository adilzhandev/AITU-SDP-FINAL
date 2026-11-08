package school.reports;

import java.util.Objects;

public abstract class ReportDecorator implements StudentReport {
    protected final StudentReport wrapped;

    protected ReportDecorator(StudentReport wrapped) {
        this.wrapped = Objects.requireNonNull(wrapped);
    }
}
