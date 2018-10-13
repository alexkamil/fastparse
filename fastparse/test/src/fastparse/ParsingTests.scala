package test.fastparse
import fastparse._
import utest._
import NoWhitespace._
object ParsingTests extends TestSuite{


  import Parsed.{Success, Failure}

  def check[T](parser: P[_] => P[T], input: (String, Int), rhs: Parsed[T]) = {
    val (str, index) = input
    // Test normal parsing
//    val parsed = parse(str, index).read(parser(_))
//    assert(parsed == rhs)
    // Test iterator parsing
    for(chunkSize <- Seq(1)){
      val parsed = parseIterator(str.grouped(chunkSize), index).read(parser(_))
      assert(parsed == rhs)
    }
  }
  def checkFail[T](parser: P[_] => P[T], input: (String, Int), expectedFailureIndex: Int) = {
    val (str, index) = input
    // Test normal parsing
    val parsed = parse(str, index).read(parser(_))
    val failureIndex = parsed.asInstanceOf[Parsed.Failure].index
    assert(failureIndex == expectedFailureIndex)
    // Test iterator parsing
    for(chunkSize <- Seq(1, 4, 16, 64, 256, 1024)){
      val parsed = parseIterator(str.grouped(chunkSize), index).read(parser(_))
      val failureIndex = parsed.asInstanceOf[Parsed.Failure].index
      assert(failureIndex == expectedFailureIndex)
    }

  }
  val tests = Tests {


    'literal - {
      checkFail(implicit c => "Hello WOrld!", ("Hello", 0), 0)
      check(implicit c => "Hello", ("Hello WOrld!", 0), Success((), 5))
      check(implicit c => "H", ("Hello WOrld!", 0), Success((), 1))
      check(implicit c => "Hello".!, ("Hello WOrld!", 0), Success("Hello", 5))
      val variable = "Hello"
      check(implicit c => variable.!, ("Hello WOrld!", 0), Success("Hello", 5))
      checkFail(implicit c => "Hello", ("Hello WOrld!", 5), 5)
      check(implicit c => " WO".!, ("Hello WOrld!", 5), Success(" WO", 8))
    }
    'literalIgnoreCase - {
      checkFail(implicit c => IgnoreCase("Hello WOrld!"), ("hElLo", 0), 0)
      check(implicit c => IgnoreCase("Hello"), ("hElLo WOrld!", 0), Success((), 5))
      check(implicit c => IgnoreCase("Hello").!, ("hElLo WOrld!", 0), Success("hElLo", 5))
      checkFail(implicit c => IgnoreCase("Hello"), ("hElLo WOrld!", 5), 5)
      check(implicit c => IgnoreCase(" wo").!, ("Hello WOrld!", 5), Success(" WO", 8))
      check(implicit c => IgnoreCase("`~@!3#$4%^&*()-_=+[{]}|\\,.? Hello World"), ("`~@!3#$4%^&*()-_=+[{]}|\\,.? hElLo wOrLd", 0), Success((), 39))
    }
    'repeat - {
      check(implicit c => "Hello".!.rep, ("HelloHello!", 0), Success(Seq("Hello", "Hello"), 10))
      check(implicit c => "Hello".!.rep, ("HelloHello!", 2), Success(Seq(), 2))
      check(implicit c => "Hello".!.rep, ("HelloHello!", 5), Success(Seq("Hello"), 10))
      check(implicit c => "Hello".!.rep(1), ("HelloHello!", 0), Success(Seq("Hello", "Hello"), 10))
      check(implicit c => "Hello".!.rep(1, max = 1), ("HelloHello!", 0), Success(Seq("Hello"), 5))
      check(implicit c => "Hello".!.rep(1, max = 2), ("HelloHello!", 0), Success(Seq("Hello", "Hello"), 10))
      check(implicit c => "Hello".!.rep(1, max = 2), ("HelloHelloHello!", 0), Success(Seq("Hello", "Hello"), 10))

      check(implicit c => "Hello".!.rep(0, max=0), ("HelloHello!", 0), Success(Seq(), 0))
      // identical :  check( ("Hello" | Pass).!, ("HelloHello!", 0), Success("Hello", 5))
      check(implicit c => "Hello".!.rep(0, max=1), ("HelloHello!", 0), Success(Seq("Hello"), 5))

      checkFail(implicit c => "Hello".rep(1), ("HelloHello!", 2), 2)
      checkFail(implicit c => "Hello".rep ~ "bye" ~ End, ("HelloHello!", 0), 10)
    }
    'either - {
      check(implicit c => "Hello".! | "Bye".!, ("HelloBye", 0), Success("Hello", 5))
      check(implicit c => ("Hello" | "Bye").!, ("HelloBye", 5), Success("Bye", 8))
      checkFail(implicit c => "Hello" | "Bye", ("HelloBye", 2), 2)
      check(implicit c => ("Hello" | "Bye").!.rep, ("HelloBye", 0), Success(Seq("Hello", "Bye"), 8))
      check(implicit c => ("Hello" | "Bye").rep.!, ("HelloBye", 0), Success("HelloBye", 8))
    }
    'sequence - {
      def p[_: P] = "Hello".! ~ "Bye".!
      check(implicit c => p, ("HelloBye", 0), Success(("Hello", "Bye"), 8))
      check(implicit c => "Hello".! ~ "Bye".! ~ "!", ("HelloBye!", 0), Success(("Hello", "Bye"), 9))
      check(implicit c => "Hello".! ~ "Bye".! ~ "!".!, ("HelloBye!", 0), Success(("Hello", "Bye", "!"), 9))
      checkFail(implicit c => "Hello" ~ "Bye", ("Bye", 0), 0)
    }
    'errors - {
      checkFail(implicit c => "Hello" ~ ("omg" | "bbq"), ("Hellookk", 0), 5)
      checkFail(implicit c => "Hello" ~ ("omg" | "bbq"), ("ellookk", 0), 0)
    }
    'cut - {
      'local{
        // Make sure that cuts only apply to enclosing
        'either {
          def parser[_: P] = P("hello" | "world" ~ "x" ~/ ("i" | "am" ~ "a") ~ "cow" | "moo")

          // Failing before the cut backtracks all the way out
          val Parsed.Failure(_,0,_) = parse("worldlols").read(parser(_))
          // Failing after the cut prevents backtracking
          val Parsed.Failure(_,6,_) = parse("worldxlols").read(parser(_))
          // Failing inside another nested `|` block allows backtracking,
          // but only up to the position of the cut
          val Parsed.Failure(_,6,_) = parse("worldxam").read(parser(_))
          // Failing *after* the nested `|` block again prevents backtracking
          val Parsed.Failure(_,9,_) = parse("worldxama").read(parser(_))

          def parser2[_: P] = P("hello" | "world" ~ "x" ~ ("i" | "am" ~/ "a" ~ "b") ~ "a" ~ "cow" | "moo")

          // Failing before the cut backtracks all the way out
          val Parsed.Failure(_,0,_) = parse("worldlols").read(parser2(_))
          val Parsed.Failure(_,0,_) = parse("worldxlols").read(parser2(_))
          // Failing inside the nested either's cut prevents backtracking
          val Parsed.Failure(_,8,_) = parse("worldxam").read(parser2(_))
          val Parsed.Failure(_,9,_) = parse("worldxama").read(parser2(_))
          // Failing after the nested either with the cut inside fails
          // to backtrack, because the cut has already been crossed
          val Parsed.Failure(_,11,_) = parse("worldxamaba").read(parser2(_))
        }
        'optional{
          def parser[_: P] = P("world" ~ "x" ~/ ("am" ~ "a").? ~ "cow").?

          // Failing before the cut backtracks all the way out
          val Parsed.Success((), 0) = parse("worldlols").read(parser(_))
          // Failing after the cut prevents backtracking
          val Parsed.Failure(_,6,_) = parse("worldxlols").read(parser(_))
          // Failing inside another nested `|` block allows backtracking,
          // but only up to the position of the cut
          val Parsed.Failure(_,6,_) = parse("worldxam").read(parser(_))
          // Failing *after* the nested `|` block again prevents backtracking
          val Parsed.Failure(_,9,_) = parse("worldxama").read(parser(_))

          def parser2[_: P] = P("world" ~ "x" ~ ("am" ~/ "a" ~ "b").? ~ "a" ~ "cow").?

          // Failing before the cut backtracks all the way out
          val Parsed.Success((), 0) = parse("worldlols").read(parser2(_))
          val Parsed.Success((), 0) = parse("worldxlols").read(parser2(_))
          // Failing inside the nested either's cut prevents backtracking
          val Parsed.Failure(_,8,_) = parse("worldxam").read(parser2(_))
          val Parsed.Failure(_,9,_) = parse("worldxama").read(parser2(_))
          // Failing after the nested either with the cut inside fails
          // to backtrack, because the cut has already been crossed
          val Parsed.Failure(_,11,_) = parse("worldxamaba").read(parser2(_))
        }
        'rep{
          def parser[_: P] = P("world" ~ "x" ~/ ("am" ~ "a").rep ~ "cow").rep

          // Failing before the cut backtracks all the way out
          val Parsed.Success((), 0) = parse("worldlols").read(parser(_))
          // Failing after the cut prevents backtracking
          val Parsed.Failure(_,6,_) = parse("worldxlols").read(parser(_))
          // Failing inside another nested `|` block allows backtracking,
          // but only up to the position of the cut
          val Parsed.Failure(_,6,_) = parse("worldxam").read(parser(_))
          // Failing *after* the nested `|` block again prevents backtracking
          val Parsed.Failure(_,9,_) = parse("worldxama").read(parser(_))

          def parser2[_: P] = P("world" ~ "x" ~ ("am" ~/ "a" ~ "b").rep ~ "a" ~ "cow").rep

          // Failing before the cut backtracks all the way out
          val Parsed.Success((), 0) = parse("worldlols").read(parser2(_))
          val Parsed.Success((), 0) = parse("worldxlols").read(parser2(_))
          // Failing inside the nested either's cut prevents backtracking
          val Parsed.Failure(_,8,_) = parse("worldxam").read(parser2(_))
          val Parsed.Failure(_,9,_) = parse("worldxama").read(parser2(_))
          // Failing after the nested either with the cut inside fails
          // to backtrack, because the cut has already been crossed
          val Parsed.Failure(_,11,_) = parse("worldxamaba").read(parser2(_))
        }
      }
      'sequence - {
        check(implicit c => "Hello" ~ ("wtf" ~ "omg" | "wtfom"), ("Hellowtfom", 0), Success((), 10))
        checkFail(implicit c => "Hello" ~ ("wtf" ~ "omg" | "bbq"), ("Hellowtfom", 0), 5)
        checkFail(implicit c => "Hello" ~ ("wtf" ~/ "omg" | "wtfom"), ("Hellowtfom", 0), 8)
        checkFail(implicit c => "Hello" ~ ("wtf" ~ "omg" ~/ "bbq" | "wtfom"), ("Hellowtfomgbbe", 0), 11)
        checkFail(implicit c => "Hello" ~ ("wtf" ~/ "omg" ~ "bbq" | "wtfom"), ("Hellowtfomgbbe", 0), 11)
      }
      'rep - {
        check(implicit c => ("Hello" ~ "Bye").rep, ("HelloByeHello", 0), Success((), 8))
        checkFail(implicit c => ("Hello" ~/ "Bye").rep, ("HelloByeHello", 0), 13)
        check(implicit c => ("Hello" ~ "Bye").rep, ("HelloByeHello", 0), Success((), 8))
        checkFail(implicit c => "Hello".rep(sep = "Bye" ~/ Pass), ("HelloBye", 0), 8)
        checkFail(implicit c => "Hello".rep(sep = "Bye"./), ("HelloBye", 0), 8)
      }
      'optional - {
        check(implicit c => ("Hello" ~ "Bye").?, ("HelloBoo", 0), Success((), 0))
        checkFail(implicit c => ("Hello" ~/ "Bye").?, ("HelloBoo", 0), 5)
      }
      'flatMap - {
        checkFlatmap()
      }
      'filter - {
        checkFail(implicit c => ("Hello" ~/ "Boo").filter(_ => false) | "", ("HelloBoo", 0), 8)
      }
      'lookaheadNot - {
        // ! disables cuts: since the whole point of it is to backtrack there
        // isn't any use case where a user would *want* the cuts to take effect
        check(implicit c => !("Hello" ~/ "Bye"), ("HelloBoo", 0), Success((), 0))
        // &() disables cuts: whether it succeeds or fails, the whole point
        // of &() is to backtrack and re-parse things
        check(implicit c => &("Hello" ~/ "Bye") ~ "lol" | "", ("HelloBoo", 0), Success((), 0))
        def p[_: P] = P(  &("Hello" ~/ "Boo") ~ "lol" | "".log("<empty>") )
        check(implicit c => p, ("HelloBoo", 0), Success((), 0))
      }
    }
    'stringInIgnoreCase - {
      check(implicit c => StringInIgnoreCase("Hello", "Hello World"), ("hElLo WOrld!", 0), Success((), 11))
      check(implicit c => StringInIgnoreCase("abc","abde","abdgh").!, ("ABCDE", 0), Success(("ABC"), 3))
      checkFail(implicit c => StringInIgnoreCase("abc","def","ghi"), ("bcde", 0), 0)
    }
  }
  // Broken out of the TestSuite block to avoid problems in our 2.10.x
  // build due to https://issues.scala-lang.org/browse/SI-7987
  def checkFlatmap() = {
    checkFail(implicit c => ("Hello" ~/ "Boo").flatMap(_ => Fail).?, ("HelloBoo", 0), 8)
    checkFail(implicit c => (("Hello" ~/ "Boo").flatMap(_ => Pass) ~ Fail).?, ("HelloBoo", 0), 8)
  }
}
