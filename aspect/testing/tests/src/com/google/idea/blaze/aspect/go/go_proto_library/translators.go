package proto

import (
	"time"
)

// Time translation functions.

func ToProtoTime(t time.Time) Time {
	return Time{Value: t.Format(time.RFC3339Nano)}
}
