#include "gtest/gtest.h"

TEST(SampleSuite, SampleTest) {
  EXPECT_EQ(0, 0);
}

TEST(SampleSuite, AnotherTest) {
  EXPECT_EQ(1, 1);
}

TEST(FilterSuite, FilteredTest) {
  EXPECT_EQ(2, 2);
}

TEST(FilterSuite, SkippedTest) {
  EXPECT_EQ(3, 3);
}
