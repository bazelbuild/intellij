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
		{
			envVar:     "ENV_VAR_IN_PLAIN_RUN_CONFIG",
			validation: func(expanded string) bool { return expanded == "it works" },
			error:      "Expected value to be 'it works'",
		},
	}
	for _, testCase := range testCases {
		expanded := os.Getenv(testCase.envVar)
		if !testCase.validation(expanded) {
			t.Errorf("Failure validating expansion for env var %s: Got, %s, error is: %s", testCase.envVar, expanded, testCase.error)
		}
	}
}

func TestEnvVars(t *testing.T) {
	testCases := []struct {
		envVar     string
		validation func(string) bool
		error      string
	}{
		{
			envVar:     "ENV_VAR_IN_PLAIN_RUN_CONFIG",
			validation: func(expanded string) bool { return expanded == "it works" },
			error:      "Expected value to be 'it works'",
		},
	}
	for _, testCase := range testCases {
		expanded := os.Getenv(testCase.envVar)
		if !testCase.validation(expanded) {
			t.Errorf("Failure validating expansion for env var %s: Got, %s, error is: %s", testCase.envVar, expanded, testCase.error)
		}
	}
}

func TestCountExternalCats(t *testing.T) {
	testCases := []struct {
		name          string
		expectedCount int
	}{
		{
			name:          "Cats",
			expectedCount: 2,
		},
	}
	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			count := CountExternalCats()
			if count != tc.expectedCount {
				t.Errorf("Expected to have %d cats, got %d cats", tc.expectedCount, count)
			}
		})
	}

}
