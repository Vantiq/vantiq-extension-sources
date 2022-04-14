import pathlib
from setuptools import setup

# The directory containing this file
HERE = pathlib.Path(__file__).parent

# The text of the README file
README = (HERE / "README.md").read_text()

setup(
    name='vantiq-connector-sdk',
    version='0.9.1',
    description="SDK for building Vantiq extension sources/connectors in Python",
    long_description=README,
    url='https://github.com/Vantiq/vantiq-extension-sources',
    packages=['vantiq', 'vantiq.extpsdk'],
    package_dir={
        'vantiq': 'src/main/python/vantiq',
        'vantiq.extpsdk': 'src/main/python/vantiq/extpsdk'
    },
    license="MIT",
    classifiers=[
        "License :: OSI Approved :: MIT License",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
    ],
    install_requires=[
        "websockets>=10.2",
        "jprops>=2.0.2"
    ],
    entry_points={},
)