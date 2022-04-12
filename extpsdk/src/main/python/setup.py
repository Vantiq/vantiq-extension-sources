from setuptools import setup

setup(
    name='vantiq-connector',
    version='0.9.0',
    packages=['vantiq.extpsdk'],
    install_requires=[
        'requests',
        'importlib-metadata; python_version == "3.10"',
        'vantiq.extpsdk'
    ],
)