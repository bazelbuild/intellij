genrule(
    name = "generate_hello_java",
    outs = ["greetings/HelloGenerated.java"],
    cmd = """
    cat << EOF >> $@
package greetings;

import java.util.Arrays;

public class HelloGenerated {
    public static void main(String[] args) {
        System.out.println(Arrays.toString(args) + " and Hello");
    }

    public String getMessage() {
        return "Generated Hello!";
    }
}
EOF
""",
)

java_library(
    name = "generated_hello_lib",
    srcs = [":generate_hello_java"],
    visibility = ["//visibility:public"]
)

java_binary(
    name = "generated_hello_bin",
    main_class = "greetings.HelloGenerated",
    runtime_deps = [":generated_hello_lib"],
)
