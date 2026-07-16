#include <gtest/gtest.h>

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

template<typename T>
class TypedTestSuite : public ::testing::Test {};

using MyTypes = ::testing::Types<int, char>;
TYPED_TEST_SUITE(TypedTestSuite, MyTypes);

TYPED_TEST(TypedTestSuite, SampleTest) {
  EXPECT_EQ(4, 4);
}

TYPED_TEST(TypedTestSuite, AnotherTest) {
  EXPECT_EQ(5, 5);
}
