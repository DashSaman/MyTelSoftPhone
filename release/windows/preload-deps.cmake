# MyTel: pre-load the prebuilt SDK's imported targets in dependency order.
# The 5.5.x SDK packages include their Targets file BEFORE find_dependency,
# so a cold find_package(BelCard) sees unresolved `belr` references.
# NOTE: LibLinphone is deliberately NOT preloaded — we build it from source.
#
# The SDK was built in RelWithDebInfo; every imported target therefore only
# carries RELWITHDEBINFO properties. Our Ninja build is single-config Release,
# so map Release -> RelWithDebInfo or imported targets would link empty.
set(CMAKE_MAP_IMPORTED_CONFIG_RELEASE RelWithDebInfo)

if(NOT MYTEL_SDK_PREFIX)
    message(FATAL_ERROR "Set MYTEL_SDK_PREFIX to the extracted Linphone SDK win64 directory")
endif()
set(_MYTEL_SDK_PREFIX "${MYTEL_SDK_PREFIX}")
foreach(_pkg BCToolbox Belr BelleSIP Ortp Mediastreamer2 BZRTP Lime Belcard
             SQLITE3 Decaf MbedTLS)
    find_package(${_pkg} CONFIG QUIET)
endforeach()

# Several SDK components ship only raw Targets files (no Config package).
# Include them directly and bridge to the namespaced names that liblinphone's
# bc_find_package prefers, so CMake's bundled (empty) Find modules never win.
include(${_MYTEL_SDK_PREFIX}/share/SQLITE3/cmake/Sqlite3Targets.cmake OPTIONAL)
# jsoncpp ships raw targets defining `jsoncpp_lib`, referenced by the freshly
# built liblinphone's INTERFACE_LINK_LIBRARIES.
include(${_MYTEL_SDK_PREFIX}/lib/cmake/jsoncpp/jsoncpp-targets.cmake OPTIONAL)
if(TARGET sqlite3 AND NOT TARGET SQLite::SQLite3)
    set_property(TARGET sqlite3 PROPERTY IMPORTED_GLOBAL TRUE)
    add_library(SQLite::SQLite3 ALIAS sqlite3)
endif()

include(${_MYTEL_SDK_PREFIX}/cmake/SOCI.cmake OPTIONAL)
if(TARGET SOCI::soci_core AND NOT TARGET soci)
    add_library(soci INTERFACE)
    target_link_libraries(soci INTERFACE SOCI::soci_core)
endif()
if(TARGET SOCI::soci_sqlite3 AND NOT TARGET soci_sqlite3)
    add_library(soci_sqlite3 INTERFACE)
    target_link_libraries(soci_sqlite3 INTERFACE SOCI::soci_sqlite3)
endif()

include(${_MYTEL_SDK_PREFIX}/share/xerces-c/cmake/XercesCTargets.cmake OPTIONAL)
# target name `xerces-c` matches bc_find_package's last fallback directly.

include(${_MYTEL_SDK_PREFIX}/share/XML2/cmake/XML2Targets.cmake OPTIONAL)
if(TARGET xml2 AND NOT TARGET LibXml2::LibXml2)
    # SDK ships headers under include/libxml2/libxml/... but the target
    # advertises only <prefix>/include — fix the include path accordingly.
    set_property(TARGET xml2 PROPERTY
        INTERFACE_INCLUDE_DIRECTORIES "${_MYTEL_SDK_PREFIX}/include/libxml2")
    set_property(TARGET xml2 PROPERTY IMPORTED_GLOBAL TRUE)
    add_library(LibXml2::LibXml2 ALIAS xml2)
endif()

include(${_MYTEL_SDK_PREFIX}/share/zlib/cmake/ZLIBTargets.cmake OPTIONAL)
if(TARGET zlib AND NOT TARGET ZLIB::ZLIB)
    set_property(TARGET zlib PROPERTY IMPORTED_GLOBAL TRUE)
    add_library(ZLIB::ZLIB ALIAS zlib)
endif()
