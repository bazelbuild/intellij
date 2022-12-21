// Testing Package
//
// This file tests varied configurations of tests.
// One should be able to click any of the '>' buttons
// on the right margin and have that specific test or subtest run,
// and nothing else.
package lib

import (
	"log"
	"testing"
)

func TestAdd(t *testing.T) {
	t.Run("2 + 3 = 5", func(t *testing.T) {
		got := AddToTwo(3)
		if got != 5 {
			t.Fatalf("Maths are broken")
		}
	})
	t.Run("2 + (-3) = -1", func(t *testing.T) {
		got := AddToTwo(-3)
		if got != -1 {
			t.Fatalf("Maths are broken")
		}
	})
	t.Run("with_nested", func(t *testing.T) {
		// TestAdd/with_nested/subtest
		t.Run("subtest", func(t *testing.T) {})
	})
	t.Run("This should always fail", func(t *testing.T) {
		t.Fatalf("Failing on purpose")
	})

}

func TestFoo(t *testing.T) {
	t.Run("with_nested", func(t *testing.T) {
		t.Run("subtest", func(t *testing.T) {})
	})
}

func TestSub(t *testing.T) {
	t.Run("2 - (-3) = 5", func(t *testing.T) {
		got := SubToTwo(-3)
		if got != 5 {
			t.Fatalf("Maths are broken")
		}
	})
}

func TestMultipleCases(t *testing.T) {
	testCases := []struct {
		name string
		fn   func(*testing.T)
	}{
		{
			name: "TestCase1",
			fn:   func(t *testing.T) { log.Println("I Succeed! ") },
		},
		{
			name: "TestCase2",
			fn:   func(t *testing.T) { t.Fatal("I fail! ") },
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			log.Println("running test: " + tc.name)
			tc.fn(t)
		})
	}
}
