cmake_minimum_required(VERSION 3.24)
project(MytellQtVideoRenderer LANGUAGES CXX)

# Build the upstream optional ENABLE_QT_GL module against the already installed
# SDK. This deliberately does not rebuild or replace its media engine or codecs.
set(MYTEL_SDK_ROOT "" CACHE PATH "Installed Linphone win64 SDK prefix")
set(MYTEL_MS2_SOURCE "" CACHE PATH "Corresponding mediastreamer2 source directory")
if(NOT EXISTS "${MYTEL_MS2_SOURCE}/src/videofilters/msqogl.cpp")
    message(FATAL_ERROR "MYTEL_MS2_SOURCE must contain the upstream Qt renderer")
endif()
find_package(Qt6 6.10 REQUIRED COMPONENTS Core Gui Quick OpenGL)
add_library(msqogl MODULE "${MYTEL_MS2_SOURCE}/src/videofilters/msqogl.cpp")
set_target_properties(msqogl PROPERTIES PREFIX "lib" CXX_STANDARD 17 CXX_STANDARD_REQUIRED YES)
target_compile_definitions(msqogl PRIVATE VERSION="5.5.39-mytell-qt6.10")
target_include_directories(msqogl PRIVATE
    "${MYTEL_SDK_ROOT}/include"
    "${MYTEL_SDK_ROOT}/include/OpenGL"
    "${MYTEL_MS2_SOURCE}/src/utils")
target_link_libraries(msqogl PRIVATE
    "${MYTEL_SDK_ROOT}/lib/mediastreamer2.lib"
    "${MYTEL_SDK_ROOT}/lib/bctoolbox.lib"
    "${MYTEL_SDK_ROOT}/lib/ortp.lib"
    Qt6::Core Qt6::Gui Qt6::Quick Qt6::OpenGL)
install(TARGETS msqogl RUNTIME DESTINATION lib/mediastreamer/plugins
                       LIBRARY DESTINATION lib/mediastreamer/plugins)
