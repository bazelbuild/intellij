#include <gtest/gtest.h>
#include "greeting_lib.h"

TEST(GreetingTest, OneName) {
    std::vector<std::string> names;
    names.push_back("world");
    EXPECT_STREQ(get_greet(names).c_str(), "Hello world");
}

TEST(GreetingTest, MultipleNames) {
    std::vector<std::string> names;
    names.push_back("name1");
    names.push_back("name2");
    names.push_back("name3");
    EXPECT_STREQ(get_greet(names).c_str(), "Hello name1 name2 name3");
}