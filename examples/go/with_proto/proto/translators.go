package proto

import (
	"time"
)

// Time translation functions.
// If all symbols resolve correctly (i.e. there are no red squigglies after a sync)
// then this target is working.
func ToProtoTime(t time.Time) Time {
	return Time{Value: t.Format(time.RFC3339Nano)}
}
