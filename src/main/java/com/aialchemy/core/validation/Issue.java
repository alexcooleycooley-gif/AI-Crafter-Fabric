package com.aialchemy.core.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * One validation finding. Warnings are recorded for admins and logs; errors reject the response and
 * move the pipeline on to its next retry or fallback.
 */
public record Issue(Severity severity, String code, String message) {

	public enum Severity {
		WARNING,
		ERROR
	}

	public static Issue error(String code, String message) {
		return new Issue(Severity.ERROR, code, message);
	}

	public static Issue warning(String code, String message) {
		return new Issue(Severity.WARNING, code, message);
	}

	/** Small collector used by the individual validators. */
	public static final class Collector {

		private final List<Issue> issues = new ArrayList<>();

		public void error(String code, String message) {
			this.issues.add(Issue.error(code, message));
		}

		public void warning(String code, String message) {
			this.issues.add(Issue.warning(code, message));
		}

		public boolean hasErrors() {
			return this.issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
		}

		public List<Issue> issues() {
			return List.copyOf(this.issues);
		}

		public String errorSummary() {
			StringBuilder builder = new StringBuilder();

			for (Issue issue : this.issues) {
				if (issue.severity() != Severity.ERROR) {
					continue;
				}

				if (builder.length() > 0) {
					builder.append("; ");
				}

				builder.append(issue.code()).append(": ").append(issue.message());
			}

			return builder.toString();
		}
	}
}