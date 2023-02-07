package main

import (
	"os"
	"testing"
)

func TestFlagMacros(t *testing.T) {
	testCases := []struct {
		envVar     string
		validation func(string) bool
		error      string
	}{
		{
			envVar:     "PROJECT_NAME",
			validation: func(expanded string) bool { return expanded == "with_proto" },
			error:      "Expected value to be 'with_proto'",
		},
		{
			envVar:     "WORKSPACE_ROOT",
			validation: func(expanded string) bool { return expanded != "$WorkspaceRoot" },
			error:      "$WorkspaceRoot$ macro didn't get expanded",
		},
	}
	for _, testCase := range testCases {
		expanded := os.Getenv(testCase.envVar)
		if !testCase.validation(expanded) {
			t.Errorf("Failure validating expansion for env var %s: Got, %s, error is: %s", testCase.envVar, expanded, testCase.error)
		}
	}
}
