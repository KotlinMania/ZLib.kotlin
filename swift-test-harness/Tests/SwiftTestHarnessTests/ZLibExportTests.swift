#if canImport(Testing)
import Testing
import ZLib

@Suite("ZLib Swift Export Suite")
struct ZLibExportTests {
    @Test("Swift module loads cleanly")
    func swiftModuleLoads() {
        #expect(Bool(true), "ZLib swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import ZLib

final class ZLibExportTests: XCTestCase {
    func testSwiftModuleLoads() {
        XCTAssertTrue(true, "ZLib swift module imported cleanly")
    }
}
#endif
