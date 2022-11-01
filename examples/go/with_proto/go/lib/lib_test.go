package lib

import "testing"

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
}

func TestSub(t *testing.T) {
	t.Run("2 - (-3) = 5", func(t *testing.T) {
		got := SubToTwo(-3)
		if got != 5 {
			t.Fatalf("Maths are broken")
		}
	})
}
