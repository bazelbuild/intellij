// Package lib .
package lib

import (
	barpb "google3/javatests/com/google/devtools/intellij/blaze/plugin/aspect/go/goprotolibrary/bar_go_proto"
	bazpb "google3/javatests/com/google/devtools/intellij/blaze/plugin/aspect/go/goprotolibrary/baz_go_proto"
)

func placeHolder(bar *barpb.Msg) *bazpb.Msg {
	return &bazpb.Msg{Str: bar.GetStr()}
}
