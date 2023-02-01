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
			envVar:     "FILE_NAME",
			validation: func(expanded string) bool { return expanded == "main_test.go" },
			error:      "Expected value to be 'main_test.go'",
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
