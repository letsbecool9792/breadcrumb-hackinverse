import assert from "node:assert/strict";
import { describe, test } from "node:test";
import { linkSites, registrableDomain, siteName } from "./sites.ts";

describe("site names", () => {
  test("a site is named as people say it, whatever host or short link it came by", () => {
    assert.deepEqual(linkSites("https://www.instagram.com/p/abc and https://instagr.am/p/def"), ["Instagram"]);
    assert.deepEqual(linkSites("https://youtu.be/xyz https://m.youtube.com/watch?v=1"), ["YouTube"]);
    assert.deepEqual(linkSites("https://twitter.com/a/status/1"), ["X"]);
  });

  test("an unlisted site is named by its first label", () => {
    assert.equal(siteName("blog.medium.com"), "Medium");
    assert.equal(siteName("www.bbc.co.uk"), "Bbc");
    assert.equal(registrableDomain("www.flipkart.co.in"), "flipkart.co.in");
  });

  test("bare www addresses count, and every site appears once, in order", () => {
    assert.deepEqual(
      linkSites("see www.example.org/a, then https://github.com/x and https://example.org/b"),
      ["Example", "GitHub"],
    );
  });

  test("addresses with no name give none", () => {
    assert.deepEqual(linkSites("http://192.168.1.4:3000/x http://localhost/y"), []);
    assert.deepEqual(linkSites("no links here"), []);
    assert.deepEqual(linkSites(null), []);
  });
});
