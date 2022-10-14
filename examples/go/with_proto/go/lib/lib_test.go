package lib

import "testing"

func TestMyFun(t *testing.T) {
	t.Run("3 + 2 = 5", func(t *testing.T) {
		got := AddToTwo(3)
		if got != 5 {
			t.Fatalf("Maths are broken")
		}
	})
	t.Run("-3 + 2 = -1", func(t *testing.T) {
		got := AddToTwo(-3)
		if got != -1 {
			t.Fatalf("Maths are broken")
		}
	})
}
